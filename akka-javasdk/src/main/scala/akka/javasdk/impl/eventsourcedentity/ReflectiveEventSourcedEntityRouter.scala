/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.eventsourcedentity

import akka.annotation.InternalApi
import akka.javasdk.eventsourcedentity.CommandContext
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.impl.InvocationContext
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.impl.StrictJsonMessageCodec
import akka.javasdk.impl.reflection.Reflect
import com.google.protobuf.any.{ Any => ScalaPbAny }

/**
 * INTERNAL API
 */
@InternalApi
private[impl] class ReflectiveEventSourcedEntityRouter[S, E, ES <: EventSourcedEntity[S, E]](
    override val entity: ES,
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

    _setCurrentState(state)

    event match {
      case anyPb: ScalaPbAny => // replaying event coming from runtime
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

    _setCurrentState(state)

    val commandHandler = commandHandlerLookup(commandName)

    val scalaPbAnyCommand = command.asInstanceOf[ScalaPbAny]
    if (AnySupport.isJson(scalaPbAnyCommand)) {
      // special cased component client calls, lets json commands through all the way
      val methodInvoker = commandHandler.getSingleNameInvoker()
      val deserializedCommand =
        CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, scalaPbAnyCommand)
      val result = deserializedCommand match {
        case None          => methodInvoker.invoke(entity)
        case Some(command) => methodInvoker.invokeDirectly(entity, command)
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

  private def _setCurrentState(state: S): Unit = {
    val entityStateType: Class[S] = Reflect.eventSourcedEntityStateType(this.entity.getClass).asInstanceOf[Class[S]]

    // the state: S received can either be of the entity "state" type (if coming from emptyState/memory)
    // or PB Any type (if coming from the runtime)
    state match {
      case s if s == null || state.getClass == entityStateType =>
        // note that we set the state even if null, this is needed in order to
        // be able to call currentState() later
        entity._internalSetCurrentState(s)
      case s =>
        // FIXME this case should not be needed, maybe remove the type check
        throw new IllegalArgumentException(
          s"Unexpected state type [${s.getClass.getName}], expected [${entityStateType.getName}]")
//        val deserializedState =
//          JsonSupport.decodeJson(entityStateType, ScalaPbAny.toJavaProto(s.asInstanceOf[ScalaPbAny]))
//        entity._internalSetCurrentState(deserializedState)
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class HandlerNotFoundException(handlerType: String, name: String, availableHandlers: Set[String])
    extends RuntimeException(
      s"no matching $handlerType handler for '$name'. " +
      s"Available handlers are: [${availableHandlers.mkString(", ")}]")
