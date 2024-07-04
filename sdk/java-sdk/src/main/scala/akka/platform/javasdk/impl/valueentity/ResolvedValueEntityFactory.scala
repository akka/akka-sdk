/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.valueentity

import akka.platform.javasdk.impl.ResolvedEntityFactory
import akka.platform.javasdk.impl.ResolvedServiceMethod
import akka.platform.javasdk.impl.ValueEntityFactory
import akka.platform.javasdk.valueentity.ValueEntityContext

class ResolvedValueEntityFactory(
    delegate: ValueEntityFactory,
    override val resolvedMethods: Map[String, ResolvedServiceMethod[_, _]])
    extends ValueEntityFactory
    with ResolvedEntityFactory {

  override def create(context: ValueEntityContext): ValueEntityRouter[_, _] =
    delegate.create(context)
}
