/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.eventsourcedentity

import java.lang.reflect.ParameterizedType
import com.google.protobuf.any.{ Any => ScalaPbAny }
import akka.platform.javasdk.JsonSupport
import akka.platform.javasdk.eventsourcedentity.CommandContext
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity
import akka.platform.javasdk.impl.CommandHandler
import akka.platform.javasdk.impl.InvocationContext
import akka.platform.javasdk.impl.JsonMessageCodec
import akka.platform.javasdk.impl.StrictJsonMessageCodec
import akka.platform.javasdk.impl.reflection.Reflect

import scala.util.control.NonFatal

class ReflectiveEventSourcedEntityRouter[S, E, ES <: EventSourcedEntity[S, E]](
    override protected val entity: ES,
    commandHandlers: Map[String, CommandHandler],
    messageCodec: JsonMessageCodec)
    extends EventSourcedEntityRouter[S, E, ES](entity) {

  private val strictCodec = new StrictJsonMessageCodec(messageCodec)

  // similar to workflow, we preemptively register the events type to the message codec
  Reflect.allKnownEventTypes[S, E, ES](entity).foreach(messageCodec.registerTypeHints)

  private def commandHandlerLookup(commandName: String) =
    commandHandlers.getOrElse(
      commandName,
      throw new HandlerNotFoundException("command", commandName, commandHandlers.keySet))

  override def handleEvent(state: S, event: E): S = {

    _extractAndSetCurrentState(state)

    event match {
      case anyPb: ScalaPbAny => // replaying event coming from proxy
        val deserEvent = strictCodec.decodeMessage(anyPb)
        val casted = deserEvent.asInstanceOf[event.type]
        entity.applyEvent(casted)

      case _ => // processing runtime event coming from memory
        entity.applyEvent(event.asInstanceOf[event.type])

    }

  }

  override def handleCommand(
      commandName: String,
      state: S,
      command: Any,
      commandContext: CommandContext): EventSourcedEntity.Effect[_] = {

    _extractAndSetCurrentState(state)

    val commandHandler = commandHandlerLookup(commandName)

    val scalaPbAnyCommand = command.asInstanceOf[ScalaPbAny]
    if (scalaPbAnyCommand.typeUrl.startsWith(JsonSupport.KALIX_JSON)) {
      // special cased component client calls, lets json commands trough all the way
      val methodInvoker = commandHandler.getSingleNameInvoker()
      val parameterTypes = methodInvoker.method.getParameterTypes
      val result =
        if (parameterTypes.isEmpty) methodInvoker.invoke(entity)
        else if (parameterTypes.size > 1)
          throw new IllegalArgumentException(
            s"Command handler for [${entity.getClass}.$commandName] expects more than one parameter, not supported (parameter types: [${parameterTypes.mkString}]")
        else {
          // we used to dispatch based on the type, since that is how it works in protobuf for eventing
          // but here we have a concrete command name, and can pick up the expected serialized type from there
          val decodedParameter =
            try {
              JsonSupport.decodeJson(parameterTypes(0), scalaPbAnyCommand)
            } catch {
              case NonFatal(ex) =>
                throw new IllegalArgumentException(
                  s"Could not deserialize message for ${entity.getClass}.${commandName}",
                  ex)
            }
          methodInvoker.invokeDirectly(entity, decodedParameter.asInstanceOf[AnyRef])
        }
      result.asInstanceOf[EventSourcedEntity.Effect[_]]
    } else {
      // this is the old path, needed until we remove the http-grpc-handling of the static es endpoints
      val invocationContext =
        InvocationContext(scalaPbAnyCommand, commandHandler.requestMessageDescriptor, commandContext.metadata())

      val inputTypeUrl = command.asInstanceOf[ScalaPbAny].typeUrl
      val methodInvoker = commandHandler
        .getInvoker(inputTypeUrl)

      methodInvoker
        .invoke(entity, invocationContext)
        .asInstanceOf[EventSourcedEntity.Effect[_]]
    }
  }

  private def _extractAndSetCurrentState(state: S): Unit = {
    val entityStateType: Class[S] =
      this.entity.getClass.getGenericSuperclass
        .asInstanceOf[ParameterizedType]
        .getActualTypeArguments
        .head
        .asInstanceOf[Class[S]]

    // the state: S received can either be of the entity "state" type (if coming from emptyState/memory)
    // or PB Any type (if coming from the proxy)
    state match {
      case s if s == null || state.getClass == entityStateType =>
        // note that we set the state even if null, this is needed in order to
        // be able to call currentState() later
        entity._internalSetCurrentState(s)
      case s =>
        val deserializedState =
          JsonSupport.decodeJson(entityStateType, ScalaPbAny.toJavaProto(s.asInstanceOf[ScalaPbAny]))
        entity._internalSetCurrentState(deserializedState)
    }
  }
}

final class HandlerNotFoundException(handlerType: String, name: String, availableHandlers: Set[String])
    extends RuntimeException(
      s"no matching $handlerType handler for '$name'. " +
      s"Available handlers are: [${availableHandlers.mkString(", ")}]")
