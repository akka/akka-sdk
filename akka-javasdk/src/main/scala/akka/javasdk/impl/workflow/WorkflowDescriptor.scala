/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import java.lang.reflect.Method

import scala.annotation.nowarn
import scala.jdk.CollectionConverters.ListHasAsScala

import akka.javasdk.annotations.StepName
import akka.javasdk.impl.workflow.WorkflowDescriptor.stepMethodName
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.Step
import akka.javasdk.workflow.Workflow.StepEffect
import akka.javasdk.workflow.Workflow.StepMethod

object WorkflowDescriptor {

  def stepMethodName(method: Method): String = {
    val stepNameAnnotation = method.getAnnotation(classOf[StepName])
    if (stepNameAnnotation != null) {
      stepNameAnnotation.value()
    } else {
      method.getName
    }
  }
}

class WorkflowDescriptor(workflow: Workflow[_]) {

  // lookup for StepMethods in this class, and its super classes
  // stops when it reaches Any
  private val stepMethods = {
    def allMethods(c: Class[_]): Seq[Method] = {
      if (c == null || c == classOf[Object]) Seq.empty
      else c.getDeclaredMethods.toSeq ++ allMethods(c.getSuperclass) ++ c.getInterfaces.flatMap(allMethods)
    }

    allMethods(workflow.getClass)
      .filter(m => m.getReturnType == classOf[StepEffect])
      .map { m =>
        new StepMethod(stepMethodName(m), m)
      }
      .toList
  }

  @nowarn("msg=deprecated")
  def findStepByName(name: String): Option[Step] =
    workflow.definition().getSteps.asScala.find(_.name() == name)

  def findStepMethodByName(name: String): Option[StepMethod] =
    stepMethods.find(_.methodName() == name)
}
