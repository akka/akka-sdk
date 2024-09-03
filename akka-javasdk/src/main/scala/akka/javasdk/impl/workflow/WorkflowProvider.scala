/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import akka.annotation.InternalApi
import akka.javasdk.annotations.ComponentId
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.WorkflowContext
import com.google.protobuf.Descriptors

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class WorkflowProvider[S, W <: Workflow[S]](
    workflowClass: Class[W],
    messageCodec: JsonMessageCodec,
    factory: Function[WorkflowContext, W]) {
  private val annotation: ComponentId = workflowClass.getAnnotation(classOf[ComponentId])
  if (annotation == null) {
    throw new IllegalArgumentException("Workflow [" + workflowClass.getName + "] is missing '@Type' annotation")
  }
  private val componentDescriptor = ComponentDescriptor.descriptorFor(workflowClass, messageCodec)
  val serviceDescriptor: Descriptors.ServiceDescriptor = componentDescriptor.serviceDescriptor

  private def newRouter(context: WorkflowContext): WorkflowRouter[S, W] = {
    val workflow: W = factory.apply(context)
    new ReflectiveWorkflowRouter[S, W](workflow, componentDescriptor.commandHandlers)
  }

  def newServiceInstance(): WorkflowService = {
    val typeId = annotation.value
    new WorkflowService(newRouter _, serviceDescriptor, Array(componentDescriptor.fileDescriptor), messageCodec, typeId)
  }
}
