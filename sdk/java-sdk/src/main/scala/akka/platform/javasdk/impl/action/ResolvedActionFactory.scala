/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.action

import akka.platform.javasdk.impl.ResolvedEntityFactory
import akka.platform.javasdk.impl.ResolvedServiceMethod
import akka.platform.javasdk.action.ActionCreationContext
import akka.platform.javasdk.impl.ActionFactory

class ResolvedActionFactory(
    delegate: ActionFactory,
    override val resolvedMethods: Map[String, ResolvedServiceMethod[_, _]])
    extends ActionFactory
    with ResolvedEntityFactory {
  override def create(context: ActionCreationContext): ActionRouter[_] =
    delegate.create(context)

}
