/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.timedaction

import akka.platform.javasdk.impl.ResolvedEntityFactory
import akka.platform.javasdk.impl.ResolvedServiceMethod
import akka.platform.javasdk.impl.TimedActionFactory
import akka.platform.javasdk.timedaction.TimedActionContext

class ResolvedTimedActionFactory(
    delegate: TimedActionFactory,
    override val resolvedMethods: Map[String, ResolvedServiceMethod[_, _]])
    extends TimedActionFactory
    with ResolvedEntityFactory {
  override def create(context: TimedActionContext): TimedActionRouter[_] =
    delegate.create(context)

}
