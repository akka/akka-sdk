/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.consumer

import akka.platform.javasdk.consumer.ConsumerContext
import akka.platform.javasdk.impl.ConsumerFactory
import akka.platform.javasdk.impl.ResolvedEntityFactory
import akka.platform.javasdk.impl.ResolvedServiceMethod

class ResolvedConsumerFactory(
    delegate: ConsumerFactory,
    override val resolvedMethods: Map[String, ResolvedServiceMethod[_, _]])
    extends ConsumerFactory
    with ResolvedEntityFactory {
  override def create(context: ConsumerContext): ConsumerRouter[_] =
    delegate.create(context)

}
