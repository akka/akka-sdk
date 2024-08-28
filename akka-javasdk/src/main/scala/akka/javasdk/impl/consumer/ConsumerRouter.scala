/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import java.util.Optional
import ConsumerRouter.HandlerNotFound
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageContext
import akka.javasdk.consumer.MessageEnvelope

object ConsumerRouter {
  case class HandlerNotFound(commandName: String) extends RuntimeException
}
abstract class ConsumerRouter[A <: Consumer](protected val consumer: A) {

  /**
   * Handle a unary call.
   *
   * @param commandName
   *   The name of the command this call is for.
   * @param message
   *   The message envelope of the message.
   * @param context
   *   The message context.
   * @return
   *   A future of the message to return.
   */
  final def handleUnary(commandName: String, message: MessageEnvelope[Any], context: MessageContext): Consumer.Effect =
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
  def handleUnary(commandName: String, message: MessageEnvelope[Any]): Consumer.Effect

  //TODO rethink this part
  private def callWithContext[T](context: MessageContext)(func: () => T) = {
    // only set, never cleared, to allow access from other threads in async callbacks in the consumer
    // the same handler and consumer instance is expected to only ever be invoked for a single message
    consumer._internalSetMessageContext(Optional.of(context))
    try {
      func()
    } catch {
      case HandlerNotFound(name) =>
        throw new RuntimeException(s"No call handler found for call $name on ${consumer.getClass.getName}")
    }
  }

  def consumerClass(): Class[_] = consumer.getClass
}
