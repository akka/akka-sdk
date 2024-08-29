/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import scala.util.control.NonFatal
import scala.language.existentials
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import com.google.protobuf.ByteString
import com.google.protobuf.any.{ Any => ScalaPbAny }
import io.grpc.Status
import akka.javasdk.impl.ErrorHandling.BadRequestException
import KeyValueEntityEffectImpl.DeleteEntity
import akka.annotation.InternalApi
import akka.javasdk.JsonSupport
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ActivatableContext
import akka.javasdk.impl.AkkaSdkSettings
import akka.javasdk.impl.ComponentOptions
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.MessageCodec
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.ResolvedEntityFactory
import akka.javasdk.impl.ResolvedServiceMethod
import akka.javasdk.impl.Service
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.telemetry.Instrumentation
import akka.javasdk.impl.telemetry.KeyValueEntityCategory
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.keyvalueentity.CommandContext
import akka.javasdk.keyvalueentity.KeyValueEntityContext
import akka.javasdk.keyvalueentity.KeyValueEntityOptions
import kalix.protocol.component.Failure
import org.slf4j.LoggerFactory
import org.slf4j.MDC

// FIXME these don't seem to be 'public API', more internals?
import com.google.protobuf.Descriptors
import KeyValueEntityEffectImpl.UpdateState
import KeyValueEntityRouter.CommandResult
import akka.javasdk.Metadata
import akka.javasdk.impl.KeyValueEntityFactory
import akka.javasdk.impl.effect.MessageReplyImpl
import kalix.protocol.value_entity.ValueEntityAction.Action.Delete
import kalix.protocol.value_entity.ValueEntityAction.Action.Update
import kalix.protocol.value_entity.ValueEntityStreamIn.Message.{ Command => InCommand }
import kalix.protocol.value_entity.ValueEntityStreamIn.Message.{ Empty => InEmpty }
import kalix.protocol.value_entity.ValueEntityStreamIn.Message.{ Init => InInit }
import kalix.protocol.value_entity.ValueEntityStreamOut.Message.{ Failure => OutFailure }
import kalix.protocol.value_entity.ValueEntityStreamOut.Message.{ Reply => OutReply }
import kalix.protocol.value_entity._

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class KeyValueEntityService(
    val factory: KeyValueEntityFactory,
    override val descriptor: Descriptors.ServiceDescriptor,
    override val additionalDescriptors: Array[Descriptors.FileDescriptor],
    val messageCodec: MessageCodec,
    override val serviceName: String,
    val entityOptions: Option[KeyValueEntityOptions])
    extends Service {

  def this(
      factory: KeyValueEntityFactory,
      descriptor: Descriptors.ServiceDescriptor,
      additionalDescriptors: Array[Descriptors.FileDescriptor],
      messageCodec: MessageCodec,
      entityType: String,
      entityOptions: KeyValueEntityOptions) =
    this(factory, descriptor, additionalDescriptors, messageCodec, entityType, Some(entityOptions))

  override def resolvedMethods: Option[Map[String, ResolvedServiceMethod[_, _]]] =
    factory match {
      case resolved: ResolvedEntityFactory => Some(resolved.resolvedMethods)
      case _                               => None
    }

  override final val componentType = ValueEntities.name

  override def componentOptions: Option[ComponentOptions] = entityOptions
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class KeyValueEntitiesImpl(
    system: ActorSystem,
    val services: Map[String, KeyValueEntityService],
    configuration: AkkaSdkSettings,
    sdkDispatcherName: String)
    extends ValueEntities {

  import akka.javasdk.impl.EntityExceptions._

  private final val log = LoggerFactory.getLogger(this.getClass)

  val telemetry = Telemetry(system)
  lazy val instrumentations: Map[String, Instrumentation] = services.values.map { s =>
    (s.serviceName, telemetry.traceInstrumentation(s.serviceName, KeyValueEntityCategory))
  }.toMap

  private val pbCleanupDeletedValueEntityAfter =
    Some(com.google.protobuf.duration.Duration(configuration.cleanupDeletedValueEntityAfter))

  /**
   * One stream will be established per active entity. Once established, the first message sent will be Init, which
   * contains the entity ID, and, a state if the entity has previously persisted one. Once the Init message is sent, one
   * to many commands are sent to the entity. Each request coming in leads to a new command being sent to the entity.
   * The entity is expected to reply to each command with exactly one reply message. The entity should process commands
   * and reply to commands in the order they came in. When processing a command the entity can read and persist (update
   * or delete) the state.
   */
  override def handle(in: akka.stream.scaladsl.Source[ValueEntityStreamIn, akka.NotUsed])
      : akka.stream.scaladsl.Source[ValueEntityStreamOut, akka.NotUsed] =
    in.prefixAndTail(1)
      .flatMapConcat {
        case (Seq(ValueEntityStreamIn(InInit(init), _)), source) =>
          source.via(runEntity(init))
        case (Seq(), _) =>
          // if error during recovery in proxy the stream will be completed before init
          log.warn("Value Entity stream closed before init.")
          Source.empty[ValueEntityStreamOut]
        case (Seq(ValueEntityStreamIn(other, _)), _) =>
          throw ProtocolException(
            s"Expected init message for Key Value Entity, but received [${other.getClass.getName}]")
      }
      .recover { case error =>
        ErrorHandling.withCorrelationId { correlationId =>
          log.error(failureMessageForLog(error), error)
          ValueEntityStreamOut(OutFailure(Failure(description = s"Unexpected error [$correlationId]")))
        }
      }
      .async(sdkDispatcherName)

  private def runEntity(init: ValueEntityInit): Flow[ValueEntityStreamIn, ValueEntityStreamOut, NotUsed] = {
    val service =
      services.getOrElse(init.serviceName, throw ProtocolException(init, s"Service not found: ${init.serviceName}"))
    val router =
      service.factory.create(new KeyValueEntityContextImpl(init.entityId, system))
    val thisEntityId = init.entityId

    init.state match {
      case Some(ValueEntityInitState(stateOpt, _)) =>
        stateOpt match {
          case Some(state) =>
            val decoded = service.messageCodec.decodeMessage(state)
            router._internalSetInitState(decoded)
          case None => // no initial state
        }
      case None =>
        throw new IllegalStateException("ValueEntityInitState is mandatory")
    }

    Flow[ValueEntityStreamIn]
      .map(_.message)
      .map {
        case InCommand(command) if thisEntityId != command.entityId =>
          throw ProtocolException(command, "Receiving Value entity is not the intended recipient of command")

        case InCommand(command) =>
          val metadata = MetadataImpl.of(command.metadata.map(_.entries.toVector).getOrElse(Nil))

          if (log.isTraceEnabled) log.trace("Metadata entries [{}].", metadata.entries)
          val span = instrumentations(service.serviceName).buildSpan(service, command)

          span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
          try {
            val cmd =
              service.messageCodec.decodeMessage(
                command.payload.getOrElse(
                  // FIXME smuggling 0 arity method called from component client through here
                  ScalaPbAny.defaultInstance.withTypeUrl(JsonSupport.KALIX_JSON).withValue(ByteString.empty())))
            val context =
              new CommandContextImpl(thisEntityId, command.name, command.id, metadata, system)

            val (CommandResult(effect: KeyValueEntityEffectImpl[_]), errorCode) =
              try {
                (router._internalHandleCommand(command.name, cmd, context), None)
              } catch {
                case BadRequestException(msg) =>
                  (CommandResult(new KeyValueEntityEffectImpl[Any].error(msg)), Some(Status.Code.INVALID_ARGUMENT))
                case e: EntityException => throw e
                case NonFatal(error) =>
                  throw EntityException(command, s"Unexpected failure: $error", Some(error))
              } finally {
                context.deactivate() // Very important!
              }

            val serializedSecondaryEffect = effect.secondaryEffect match {
              case MessageReplyImpl(message, metadata) =>
                MessageReplyImpl(service.messageCodec.encodeJava(message), metadata)
              case other => other
            }

            val clientAction =
              serializedSecondaryEffect.replyToClientAction(
                command.id,
                errorCode // error code from BadRequest
              )

            serializedSecondaryEffect match {
              case _: ErrorReplyImpl[_] =>
                ValueEntityStreamOut(OutReply(ValueEntityReply(commandId = command.id, clientAction = clientAction)))

              case _ => // non-error
                val action: Option[ValueEntityAction] = effect.primaryEffect match {
                  case DeleteEntity =>
                    Some(ValueEntityAction(Delete(ValueEntityDelete(pbCleanupDeletedValueEntityAfter))))
                  case UpdateState(newState) =>
                    val newStateScalaPbAny = service.messageCodec.encodeScala(newState)
                    Some(ValueEntityAction(Update(ValueEntityUpdate(Some(newStateScalaPbAny)))))
                  case _ =>
                    None
                }

                ValueEntityStreamOut(OutReply(ValueEntityReply(command.id, clientAction, Seq.empty, action)))
            }
          } finally {
            span.foreach { s =>
              MDC.remove(Telemetry.TRACE_ID)
              s.end()
            }
          }

        case InInit(_) =>
          throw ProtocolException(init, "Value entity already initiated")

        case InEmpty =>
          throw ProtocolException(init, "Value entity received empty/unknown message")
      }
      .recover { case error =>
        ErrorHandling.withCorrelationId { correlationId =>
          LoggerFactory.getLogger(router.entityClass).error(failureMessageForLog(error), error)
          ValueEntityStreamOut(OutFailure(Failure(description = s"Unexpected error [$correlationId]")))
        }
      }
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class CommandContextImpl(
    override val entityId: String,
    override val commandName: String,
    override val commandId: Long,
    override val metadata: Metadata,
    system: ActorSystem)
    extends AbstractContext
    with CommandContext
    with ActivatableContext

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class KeyValueEntityContextImpl(override val entityId: String, system: ActorSystem)
    extends AbstractContext
    with KeyValueEntityContext
