/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import java.util.Optional
import scala.util.control.NonFatal
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.MessageCodec
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.ResolvedEntityFactory
import akka.javasdk.impl.ResolvedServiceMethod
import akka.javasdk.impl.Service
import akka.javasdk.impl.ViewFactory
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.view.UpdateContext
import akka.javasdk.view.ViewContext
import akka.stream.scaladsl.Source
import kalix.protocol.{ view => pv }
import com.google.protobuf.Descriptors
import com.google.protobuf.any.{ Any => ScalaPbAny }
import org.slf4j.LoggerFactory
import org.slf4j.MDC

import scala.jdk.OptionConverters._

/**
 * INTERNAL API
 */
@InternalApi
final class ViewService(
    val factory: Option[ViewFactory],
    override val descriptor: Descriptors.ServiceDescriptor,
    override val additionalDescriptors: Array[Descriptors.FileDescriptor],
    val messageCodec: MessageCodec,
    val viewId: String)
    extends Service {

  override def resolvedMethods: Option[Map[String, ResolvedServiceMethod[_, _]]] =
    factory.collect { case resolved: ResolvedEntityFactory =>
      resolved.resolvedMethods
    }

  override final val componentType = pv.Views.name

  override def serviceName: String = viewId
}

/**
 * INTERNAL API
 */
@InternalApi
object ViewsImpl {
  private val log = LoggerFactory.getLogger(classOf[ViewsImpl])
}

/**
 * INTERNAL API
 */
@InternalApi
final class ViewsImpl(system: ActorSystem, _services: Map[String, ViewService], sdkDispatcherName: String)
    extends pv.Views {
  import ViewsImpl.log

  private final val services = _services.iterator.toMap

  /**
   * Handle a full duplex streamed session. One stream will be established per incoming message to the view service.
   *
   * The first message is ReceiveEvent and contain the request metadata, including the service name and command name.
   */
  override def handle(in: akka.stream.scaladsl.Source[pv.ViewStreamIn, akka.NotUsed])
      : akka.stream.scaladsl.Source[pv.ViewStreamOut, akka.NotUsed] =
    // FIXME: see kalix-proxy/issues/209 and kalix-proxy/issues/207
    // It is currently only implemented to support one request (ReceiveEvent) with one response (Upsert).
    // The intention, and reason for full-duplex streaming, is that there should be able to have an interaction
    // with two main types of operations, loads, and updates, and with
    // each load there is an associated continuation, which in turn may return more operations, including more loads,
    // and so on recursively.
    in.prefixAndTail(1)
      .flatMapConcat {
        case (Seq(pv.ViewStreamIn(pv.ViewStreamIn.Message.Receive(receiveEvent), _)), _) =>
          services.get(receiveEvent.serviceName) match {
            case Some(service: ViewService) =>
              if (service.factory.isEmpty)
                throw new IllegalArgumentException(
                  s"Unexpected call to service [${receiveEvent.serviceName}] with viewId [${service.viewId}]: " +
                  "this view has `transform_updates=false` set, so updates should be handled entirely by the proxy " +
                  "and not reach the user function")

              // FIXME should we really create a new handler instance per incoming command ???
              val handler = service.factory.get.create(new ViewContextImpl)

              val state: Option[Any] =
                receiveEvent.bySubjectLookupResult.flatMap(row =>
                  row.value.map(scalaPb => service.messageCodec.decodeMessage(scalaPb)))

              val commandName = receiveEvent.commandName
              val msg = service.messageCodec.decodeMessage(receiveEvent.payload.get)
              val metadata = MetadataImpl.of(receiveEvent.metadata.map(_.entries.toVector).getOrElse(Nil))
              val addedToMDC = metadata.traceContext.traceId().toScala match {
                case Some(traceId) =>
                  MDC.put(Telemetry.TRACE_ID, traceId)
                  true
                case None => false
              }
              val context = new UpdateContextImpl(commandName, metadata)

              val effect =
                try {
                  handler._internalHandleUpdate(state, msg, context)
                } catch {
                  case e: ViewException => throw e
                  case NonFatal(error) =>
                    throw ViewException(
                      service.viewId,
                      context,
                      s"View unexpected failure: ${error.getMessage}",
                      Some(error))
                } finally {
                  if (addedToMDC) MDC.remove(Telemetry.TRACE_ID)
                }

              effect match {
                case ViewEffectImpl.Update(newState) =>
                  if (newState == null)
                    throw ViewException(service.viewId, context, "updateState with null state is not allowed.", None)
                  val serializedState = ScalaPbAny.fromJavaProto(service.messageCodec.encodeJava(newState))
                  val upsert = pv.Upsert(Some(pv.Row(value = Some(serializedState))))
                  val out = pv.ViewStreamOut(pv.ViewStreamOut.Message.Upsert(upsert))
                  Source.single(out)
                case ViewEffectImpl.Delete =>
                  val delete = pv.Delete()
                  val out = pv.ViewStreamOut(pv.ViewStreamOut.Message.Delete(delete))
                  Source.single(out)
                case ViewEffectImpl.Ignore =>
                  // ignore incoming event
                  val upsert = pv.Upsert(None)
                  val out = pv.ViewStreamOut(pv.ViewStreamOut.Message.Upsert(upsert))
                  Source.single(out)
              }

            case None =>
              val errMsg = s"Unknown service: ${receiveEvent.serviceName}"
              log.error(errMsg)
              Source.failed(new RuntimeException(errMsg))
          }

        case (Seq(), _) =>
          log.warn("View stream closed before init.")
          Source.empty[pv.ViewStreamOut]

        case (Seq(pv.ViewStreamIn(other, _)), _) =>
          val errMsg =
            s"Kalix protocol failure: expected ReceiveEvent message, but got ${other.getClass.getName}"
          Source.failed(new RuntimeException(errMsg))
      }
      .async(sdkDispatcherName)

  private final class UpdateContextImpl(override val eventName: String, override val metadata: Metadata)
      extends AbstractContext
      with UpdateContext {

    override def eventSubject(): Optional[String] =
      if (metadata.isCloudEvent)
        metadata.asCloudEvent().subject()
      else
        Optional.empty()
  }

  private final class ViewContextImpl extends AbstractContext with ViewContext

}
