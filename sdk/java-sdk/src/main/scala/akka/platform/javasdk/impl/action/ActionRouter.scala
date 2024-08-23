/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.action

import java.util.Optional

import akka.platform.javasdk.action.Action
import akka.platform.javasdk.action.MessageContext
import akka.platform.javasdk.action.MessageEnvelope
import akka.platform.javasdk.impl.action.ActionRouter.HandlerNotFound

object ActionRouter {
  case class HandlerNotFound(commandName: String) extends RuntimeException
}
abstract class ActionRouter[A <: Action](protected val action: A) {

  /**
   * Handle a unary call.
   *
   * @param commandName
   *   The name of the command this call is for.
   * @param message
   *   The message envelope of the message.
   * @param context
   *   The action context.
   * @return
   *   A future of the message to return.
   */
  final def handleUnary(commandName: String, message: MessageEnvelope[Any], context: MessageContext): Action.Effect =
    callWithContext(context) { () =>
      handleUnary(commandName, message)
    }

  /**
   * Handle a unary call.
   *
   * @param commandName
   *   The name of the command this call is for.
   * @param message
   *   The message envelope of the message.
   * @return
   *   A future of the message to return.
   */
  def handleUnary(commandName: String, message: MessageEnvelope[Any]): Action.Effect

  private def callWithContext[T](context: MessageContext)(func: () => T) = {
    // only set, never cleared, to allow access from other threads in async callbacks in the action
    // the same handler and action instance is expected to only ever be invoked for a single command
    action._internalSetMessageContext(Optional.of(context))
    try {
      func()
    } catch {
      case HandlerNotFound(name) =>
        throw new RuntimeException(s"No call handler found for call $name on ${action.getClass.getName}")
    }
  }

  def actionClass(): Class[_] = action.getClass
}
