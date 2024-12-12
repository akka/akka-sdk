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

  private def commandHandlerLookup(commandName: String): CommandHandler =
    commandHandlers.get(commandName) match {
      case Some(handler) => handler
      case None          => throw new HandlerNotFoundException("command", commandName, commandHandlers.keySet)
    }

  def handleCommand(
      commandName: String,
      command: BytesPayload,
      commandContext: CommandContext): EventSourcedEntity.Effect[_] = {

    val commandHandler = commandHandlerLookup(commandName)

    // Commands can be in three shapes:
    // - BytesPayload.empty - there is no real command, and we are calling a method with arity 0
    // - BytesPayload with json - we deserialize it and call the method
    // - BytesPayload with Proto encoding - we deserialize using InvocationContext
    if (serializer.isJson(command) || command.isEmpty) {
      // special cased component client calls, lets json commands through all the way
      val methodInvoker = commandHandler.getSingleNameInvoker()
      val deserializedCommand =
        CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, command, serializer)
      val result = deserializedCommand match {
        case None          => methodInvoker.invoke(entity)
        case Some(command) => methodInvoker.invokeDirectly(entity, command)
      }
      result.asInstanceOf[EventSourcedEntity.Effect[_]]
    } else {
      // FIXME can be proto from http-grpc-handling of the static es endpoints
      val pbAnyCommand = AnySupport.toScalaPbAny(command)
      val invocationContext =
        InvocationContext(pbAnyCommand, commandHandler.requestMessageDescriptor, commandContext.metadata())

      val inputTypeUrl = pbAnyCommand.typeUrl
      val methodInvoker = commandHandler.getInvoker(inputTypeUrl)

      methodInvoker
        .invoke(entity, invocationContext)
        .asInstanceOf[EventSourcedEntity.Effect[_]]
    }
  }

  def handleEvent(event: E): S = {
    entity.applyEvent(event.asInstanceOf[event.type])
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class HandlerNotFoundException(
    handlerType: String,
    val name: String,
    availableHandlers: Set[String])
    extends RuntimeException(
      s"no matching [$handlerType] handler for [$name]. " +
      s"Available handlers are: [${availableHandlers.mkString(", ")}]")
