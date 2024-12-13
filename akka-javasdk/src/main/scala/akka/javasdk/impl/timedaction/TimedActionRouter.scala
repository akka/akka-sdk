/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import TimedActionRouter.HandlerNotFound
import akka.annotation.InternalApi
import akka.javasdk.timedaction.CommandContext
import akka.javasdk.timedaction.CommandEnvelope
import akka.javasdk.timedaction.TimedAction
import java.util.Optional

import akka.runtime.sdk.spi.BytesPayload

/**
 * INTERNAL API
 */
@InternalApi
object TimedActionRouter {
  case class HandlerNotFound(commandName: String) extends RuntimeException
}

/**
 * INTERNAL API
 */
@InternalApi
abstract class TimedActionRouter[A <: TimedAction](protected val action: A) {

  /**
   * Handle a unary call.
   *
   * @param methodName
   *   The name of the method to call.
   * @param message
   *   The message envelope of the message.
   * @param context
   *   The action context.
   * @return
   *   A future of the message to return.
   */
  final def handleUnary(
      methodName: String,
      message: CommandEnvelope[BytesPayload],
      context: CommandContext): TimedAction.Effect =
    callWithContext(context) { () =>
      handleUnary(methodName, message)
    }

  /**
   * Handle a unary call.
   *
   * @param methodName
   *   The name of the method to call.
   * @param message
   *   The message envelope of the message.
   * @return
   *   A future of the message to return.
   */
  def handleUnary(methodName: String, message: CommandEnvelope[BytesPayload]): TimedAction.Effect

  private def callWithContext[T](context: CommandContext)(func: () => T) = {
    // only set, never cleared, to allow access from other threads in async callbacks in the action
    // the same handler and action instance is expected to only ever be invoked for a single command
    action._internalSetCommandContext(Optional.of(context))
    try {
      func()
    } catch {
      case HandlerNotFound(name) =>
        throw new RuntimeException(s"No call handler found for call $name on ${action.getClass.getName}")
    }
  }
}
