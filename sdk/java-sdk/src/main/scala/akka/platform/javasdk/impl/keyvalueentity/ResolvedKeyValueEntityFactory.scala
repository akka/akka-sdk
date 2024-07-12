/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.keyvalueentity

import akka.platform.javasdk.impl.KeyValueEntityFactory
import akka.platform.javasdk.impl.ResolvedEntityFactory
import akka.platform.javasdk.impl.ResolvedServiceMethod
import akka.platform.javasdk.keyvalueentity.KeyValueEntityContext

class ResolvedKeyValueEntityFactory(
    delegate: KeyValueEntityFactory,
    override val resolvedMethods: Map[String, ResolvedServiceMethod[_, _]])
    extends KeyValueEntityFactory
    with ResolvedEntityFactory {

  override def create(context: KeyValueEntityContext): KeyValueEntityRouter[_, _] =
    delegate.create(context)
}
