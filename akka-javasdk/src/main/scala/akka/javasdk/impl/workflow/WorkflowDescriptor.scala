/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.Step
import akka.javasdk.workflow.Workflow.StepEffect
import akka.javasdk.workflow.Workflow.StepMethod

import scala.annotation.nowarn
import scala.jdk.CollectionConverters.ListHasAsScala

class WorkflowDescriptor(workflow: Workflow[_]) {

  private val stepMethods =
    workflow.getClass.getDeclaredMethods
      .filter(_.getReturnType == classOf[StepEffect])
      .map(m => new StepMethod(m.getName, m))
      .toList

  @nowarn("msg=deprecated")
  def findStepByName(name: String): Option[Step] =
    workflow.definition().getSteps.asScala.find(_.name() == name)

  def findStepMethodByName(name: String): Option[StepMethod] =
    stepMethods.find(_.methodName() == name)
}
