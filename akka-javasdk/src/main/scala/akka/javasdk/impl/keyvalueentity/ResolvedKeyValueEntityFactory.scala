/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import akka.javasdk.impl.KeyValueEntityFactory
import akka.javasdk.impl.ResolvedEntityFactory
import akka.javasdk.impl.ResolvedServiceMethod
import akka.javasdk.keyvalueentity.KeyValueEntityContext

class ResolvedKeyValueEntityFactory(
    delegate: KeyValueEntityFactory,
    override val resolvedMethods: Map[String, ResolvedServiceMethod[_, _]])
    extends KeyValueEntityFactory
    with ResolvedEntityFactory {

  override def create(context: KeyValueEntityContext): KeyValueEntityRouter[_, _] =
    delegate.create(context)
}
