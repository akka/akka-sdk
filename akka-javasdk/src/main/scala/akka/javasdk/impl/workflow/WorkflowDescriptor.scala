/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import java.lang.reflect.Method

import akka.annotation.InternalApi
import akka.javasdk.annotations.StepName
import akka.javasdk.impl.workflow.WorkflowDescriptor.stepMethodName
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.StepEffect
import akka.javasdk.workflow.Workflow.StepMethod

/**
 * INTERNAL API
 */
@InternalApi
object WorkflowDescriptor {

  def stepMethodName(method: Method): String = {
    val stepNameAnnotation = method.getAnnotation(classOf[StepName])
    if (stepNameAnnotation != null) {
      stepNameAnnotation.value()
    } else {
      method.getName
    }
  }

  /**
   * The names of the steps of a workflow class, each once, sorted. The list leaves out methods that the compiler
   * generates, such as the body of a lambda.
   */
  def stepNames(workflowClass: Class[_]): Seq[String] =
    stepMethods(workflowClass).filterNot(_.isSynthetic).map(stepMethodName).distinct.sorted

  // The methods that return StepEffect in the class, its super classes and its interfaces.
  // The lookup stops at Object. Dispatch uses this list as is, synthetic methods included,
  // so stepNames leaves them out itself.
  private def stepMethods(workflowClass: Class[_]): Seq[Method] = {
    def allMethods(c: Class[_]): Seq[Method] = {
      if (c == null || c == classOf[Object]) Seq.empty
      else c.getDeclaredMethods.toSeq ++ allMethods(c.getSuperclass) ++ c.getInterfaces.flatMap(allMethods)
    }

    allMethods(workflowClass).filter(m => m.getReturnType == classOf[StepEffect])
  }
}

/**
 * INTERNAL API
 */
@InternalApi
class WorkflowDescriptor(workflow: Workflow[_]) {

  private val stepMethods =
    WorkflowDescriptor
      .stepMethods(workflow.getClass)
      .map { m =>
        new StepMethod(stepMethodName(m), m)
      }
      .toList

  def findStepMethodByName(name: String): Option[StepMethod] =
    stepMethods.find(_.methodName() == name)
}
