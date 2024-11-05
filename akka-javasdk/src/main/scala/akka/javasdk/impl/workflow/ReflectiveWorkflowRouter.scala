/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import akka.annotation.InternalApi
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.impl.InvocationContext
import akka.javasdk.workflow.CommandContext
import akka.javasdk.workflow.Workflow
import com.google.protobuf.any.{ Any => ScalaPbAny }

/**
 * INTERNAL API
 */
@InternalApi
class ReflectiveWorkflowRouter[S, W <: Workflow[S]](
    override protected val workflow: W,
    commandHandlers: Map[String, CommandHandler])
    extends WorkflowRouter[S, W](workflow) {

  private def commandHandlerLookup(commandName: String) =
    commandHandlers.getOrElse(
      commandName,
      throw new HandlerNotFoundException("command", commandName, commandHandlers.keySet))

  override def handleCommand(
      commandName: String,
      state: S,
      command: Any,
      commandContext: CommandContext): Workflow.Effect[_] = {

    workflow._internalSetCurrentState(state)
    val commandHandler = commandHandlerLookup(commandName)

    val scalaPbAnyCommand = command.asInstanceOf[ScalaPbAny]
    if (AnySupport.isJson(scalaPbAnyCommand)) {
      // special cased component client calls, lets json commands trough all the way
      val methodInvoker = commandHandler.getSingleNameInvoker()
      val deserializedCommand =
        CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, scalaPbAnyCommand)
      val result = deserializedCommand match {
        case None          => methodInvoker.invoke(workflow)
        case Some(command) => methodInvoker.invokeDirectly(workflow, command)
      }
      result.asInstanceOf[Workflow.Effect[_]]
    } else {

      val invocationContext =
        InvocationContext(
          command.asInstanceOf[ScalaPbAny],
          commandHandler.requestMessageDescriptor,
          commandContext.metadata())

      val inputTypeUrl = command.asInstanceOf[ScalaPbAny].typeUrl

      commandHandler
        .getInvoker(inputTypeUrl)
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
