/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import akka.annotation.InternalApi
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.impl.InvocationContext
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.workflow.CommandContext
import akka.javasdk.workflow.Workflow
import akka.runtime.sdk.spi.BytesPayload

/**
 * INTERNAL API
 */
@InternalApi
class ReflectiveWorkflowRouter[S, W <: Workflow[S]](
    override val workflow: W,
    commandHandlers: Map[String, CommandHandler],
    serializer: JsonSerializer)
    extends WorkflowRouter[S, W](workflow) {

  private def commandHandlerLookup(commandName: String) =
    commandHandlers.getOrElse(
      commandName,
      throw new HandlerNotFoundException("command", commandName, commandHandlers.keySet))

  override def handleCommand(
      commandName: String,
      state: S,
      command: BytesPayload,
      commandContext: CommandContext): Workflow.Effect[_] = {

    workflow._internalSetCurrentState(state)
    val commandHandler = commandHandlerLookup(commandName)

    // Commands can be in three shapes:
    // - BytesPayload.empty - there is no real command, and we are calling a method with arity 0
    // - BytesPayload with json - we deserialize it and call the method
    // - BytesPayload with Proto encoding - we deserialize using InvocationContext
    if (serializer.isJson(command) || command != BytesPayload.empty) {
      // special cased component client calls, lets json commands through all the way
      val methodInvoker = commandHandler.getSingleNameInvoker()
      val deserializedCommand =
        CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, command, serializer)
      val result = deserializedCommand match {
        case None        => methodInvoker.invoke(workflow)
        case Some(input) => methodInvoker.invokeDirectly(workflow, input)
      }
      result.asInstanceOf[Workflow.Effect[_]]
    } else {

      // FIXME can be proto from http-grpc-handling of the static es endpoints
      val pbAnyCommand = AnySupport.toScalaPbAny(command)
      val invocationContext =
        InvocationContext(pbAnyCommand, commandHandler.requestMessageDescriptor, commandContext.metadata())

      val inputTypeUrl = pbAnyCommand.typeUrl

      val methodInvoker = commandHandler.getInvoker(inputTypeUrl)
      methodInvoker
        .invoke(workflow, invocationContext)
        .asInstanceOf[Workflow.Effect[_]]
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
final class HandlerNotFoundException(handlerType: String, name: String, availableHandlers: Set[String])
    extends RuntimeException(
      s"no matching $handlerType handler for '$name'. " +
      s"Available handlers are: [${availableHandlers.mkString(", ")}]")
