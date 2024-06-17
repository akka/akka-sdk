/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.workflow

import com.google.protobuf.any.{ Any => ScalaPbAny }
import kalix.javasdk.JsonSupport
import kalix.javasdk.impl.CommandHandler
import kalix.javasdk.impl.InvocationContext
import kalix.javasdk.impl.workflow.WorkflowRouter
import kalix.javasdk.workflow.AbstractWorkflow
import kalix.javasdk.workflow.CommandContext
import kalix.javasdk.workflow.Workflow

import scala.util.control.NonFatal

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
      commandContext: CommandContext): AbstractWorkflow.Effect[_] = {

    workflow._internalSetCurrentState(state)
    val commandHandler = commandHandlerLookup(commandName)

    val scalaPbAnyCommand = command.asInstanceOf[ScalaPbAny]
    if (scalaPbAnyCommand.typeUrl.startsWith(JsonSupport.KALIX_JSON)) {
      // special cased component client calls, lets json commands trough all the way
      val methodInvoker = commandHandler.getSingleNameInvoker()
      val parameterTypes = methodInvoker.method.getParameterTypes
      val result =
        if (parameterTypes.isEmpty) methodInvoker.invoke(workflow)
        else if (parameterTypes.size > 1)
          throw new IllegalStateException(
            s"Handler for [$commandName] expects more than one parameter, not supported (parameter types: [${parameterTypes.mkString}]")
        else {
          // we used to dispatch based on the type, since that is how it works in protobuf for eventing
          // but here we have a concrete command name, and can pick up the expected serialized type from there
          val decodedParameter =
            try {
              JsonSupport.decodeJson(parameterTypes(0), scalaPbAnyCommand)
            } catch {
              case NonFatal(ex) =>
                throw new IllegalArgumentException(
                  s"Could not deserialize message for ${workflow.getClass}.${commandName}",
                  ex)
            }
          methodInvoker.invokeDirectly(workflow, decodedParameter.asInstanceOf[AnyRef])
        }
      result.asInstanceOf[AbstractWorkflow.Effect[_]]
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
        .asInstanceOf[AbstractWorkflow.Effect[_]]
    }
  }
}

final class HandlerNotFoundException(handlerType: String, name: String, availableHandlers: Set[String])
    extends RuntimeException(
      s"no matching $handlerType handler for '$name'. " +
      s"Available handlers are: [${availableHandlers.mkString(", ")}]")
