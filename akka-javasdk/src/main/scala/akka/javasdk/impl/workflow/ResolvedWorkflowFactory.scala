/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import akka.javasdk.impl.ResolvedEntityFactory
import akka.javasdk.impl.ResolvedServiceMethod
import akka.javasdk.impl.WorkflowFactory
import akka.javasdk.workflow.WorkflowContext

class ResolvedWorkflowFactory(
    delegate: WorkflowFactory,
    override val resolvedMethods: Map[String, ResolvedServiceMethod[_, _]])
    extends WorkflowFactory
    with ResolvedEntityFactory {

  override def create(context: WorkflowContext): WorkflowRouter[_, _] =
    delegate.create(context)
}
