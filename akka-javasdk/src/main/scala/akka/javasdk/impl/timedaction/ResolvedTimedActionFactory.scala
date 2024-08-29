/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import akka.annotation.InternalApi
import akka.javasdk.impl.ResolvedEntityFactory
import akka.javasdk.impl.ResolvedServiceMethod
import akka.javasdk.impl.TimedActionFactory
import akka.javasdk.timedaction.TimedActionContext

/**
 * INTERNAL API
 */
@InternalApi
final class ResolvedTimedActionFactory(
    delegate: TimedActionFactory,
    override val resolvedMethods: Map[String, ResolvedServiceMethod[_, _]])
    extends TimedActionFactory
    with ResolvedEntityFactory {
  override def create(context: TimedActionContext): TimedActionRouter[_] =
    delegate.create(context)

}
