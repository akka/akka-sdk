/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.eventsourcedentity

import akka.annotation.InternalApi
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.BytesPayload

/**
 * INTERNAL API
 */
@InternalApi
private[impl] class ReflectiveEventSourcedEntityRouter[S, E, ES <: EventSourcedEntity[S, E]](
    val entity: ES,
    commandHandlers: Map[String, CommandHandler],
    serializer: JsonSerializer) {

  // we preemptively register the events type to the serializer
  Reflect.allKnownEventTypes[S, E, ES](entity).foreach(serializer.registerTypeHints)

  val entityStateType: Class[S] = Reflect.eventSourcedEntityStateType(entity.getClass).asInstanceOf[Class[S]]

  private def commandHandlerLookup(commandName: String) =
    commandHandlers.getOrElse(
      commandName,
      throw new HandlerNotFoundException("command", commandName, commandHandlers.keySet))

  def handleCommand(commandName: String, command: BytesPayload): EventSourcedEntity.Effect[_] = {

    val commandHandler = commandHandlerLookup(commandName)

    // special cased component client calls, lets json commands through all the way
    val methodInvoker = commandHandler.getSingleNameInvoker()
    val deserializedCommand =
      CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, command, serializer)
    val result = deserializedCommand match {
      case None          => methodInvoker.invoke(entity)
      case Some(command) => methodInvoker.invokeDirectly(entity, command)
    }
    result.asInstanceOf[EventSourcedEntity.Effect[_]]

    // FIXME can it be proto? for example from http-grpc-handling of the static es endpoints
//      val invocationContext =
//        InvocationContext(scalaPbAnyCommand, commandHandler.requestMessageDescriptor, commandContext.metadata())
//
//      val inputTypeUrl = command.asInstanceOf[ScalaPbAny].typeUrl
//      val methodInvoker = commandHandler
//        .getInvoker(inputTypeUrl)
//
//      methodInvoker
//        .invoke(entity, invocationContext)
//        .asInstanceOf[EventSourcedEntity.Effect[_]]
  }

  def handleEvent(event: E): S = {
    event match {
      // FIXME can it be proto?
      //      case anyPb: ScalaPbAny => // replaying event coming from runtime
      //        val deserEvent = serializer.fromBytes(anyPb)
      //        val casted = deserEvent.asInstanceOf[event.type]
      //        entity.applyEvent(casted)

      case _ => // processing runtime event coming from memory
        entity.applyEvent(event.asInstanceOf[event.type])
    }
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class HandlerNotFoundException(handlerType: String, name: String, availableHandlers: Set[String])
    extends RuntimeException(
      s"no matching [$handlerType] handler for [$name]. " +
      s"Available handlers are: [${availableHandlers.mkString(", ")}]")
