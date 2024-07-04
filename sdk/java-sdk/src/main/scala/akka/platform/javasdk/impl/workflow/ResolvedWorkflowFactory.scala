/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.workflow

import akka.platform.javasdk.impl.ResolvedEntityFactory
import akka.platform.javasdk.impl.ResolvedServiceMethod
import akka.platform.javasdk.impl.WorkflowFactory
import akka.platform.javasdk.workflow.WorkflowContext

class ResolvedWorkflowFactory(
    delegate: WorkflowFactory,
    override val resolvedMethods: Map[String, ResolvedServiceMethod[_, _]])
    extends WorkflowFactory
    with ResolvedEntityFactory {

  override def create(context: WorkflowContext): WorkflowRouter[_, _] =
    delegate.create(context)
}
