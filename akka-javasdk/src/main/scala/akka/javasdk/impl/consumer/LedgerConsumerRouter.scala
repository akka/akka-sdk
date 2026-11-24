/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import java.util.Optional

import akka.annotation.InternalApi
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageContext
import akka.javasdk.impl.ComponentDescriptorFactory
import akka.javasdk.impl.MethodInvoker
import akka.javasdk.ledger.FlowRecord

/**
 * INTERNAL API
 *
 * Routes a ledger record to the handler that takes its class. Falls back to the `FlowRecord` handler.
 */
@InternalApi
private[impl] final class LedgerConsumerRouter[A <: Consumer](consumer: A, handlers: Map[Class[_], MethodInvoker]) {

  def handleRecord(record: FlowRecord, context: MessageContext): Consumer.Effect = {
    consumer._internalSetMessageContext(Optional.of(context))

    handlers.get(record.getClass).orElse(handlers.get(classOf[FlowRecord])) match {
      case Some(invoker) => invoker.invokeDirectly(consumer, record).asInstanceOf[Consumer.Effect]
      case None          => ConsumerEffectImpl.Builder.ignore()
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object LedgerConsumerRouter {

  /** The ledger record handlers of a consumer class, keyed by the record class they take. */
  def handlers(consumerClass: Class[_]): Map[Class[_], MethodInvoker] =
    consumerClass.getMethods
      .filter(method =>
        ComponentDescriptorFactory.hasConsumerOutput(method) &&
        method.getParameterCount == 1 &&
        classOf[FlowRecord].isAssignableFrom(method.getParameterTypes.head))
      .map(method => method.getParameterTypes.head -> MethodInvoker(method))
      .toMap
}
