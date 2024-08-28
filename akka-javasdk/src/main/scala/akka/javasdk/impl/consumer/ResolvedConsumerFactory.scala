/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import akka.javasdk.consumer.ConsumerContext
import akka.javasdk.impl.ConsumerFactory
import akka.javasdk.impl.ResolvedEntityFactory
import akka.javasdk.impl.ResolvedServiceMethod

class ResolvedConsumerFactory(
    delegate: ConsumerFactory,
    override val resolvedMethods: Map[String, ResolvedServiceMethod[_, _]])
    extends ConsumerFactory
    with ResolvedEntityFactory {
  override def create(context: ConsumerContext): ConsumerRouter[_] =
    delegate.create(context)

}
