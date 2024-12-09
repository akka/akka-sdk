/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import kalix.protocol.entity.Command
import kalix.protocol.workflow_entity.WorkflowEntityInit

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object WorkflowExceptions {

  final case class WorkflowException(workflowId: String, commandName: String, message: String, cause: Option[Throwable])
      extends RuntimeException(message, cause.orNull) {
    def this(workflowId: String, commandName: String, message: String) =
      this(workflowId, commandName, message, None)
  }

  object WorkflowException {

    def apply(message: String, cause: Option[Throwable]): WorkflowException =
      WorkflowException(workflowId = "", commandName = "", message, cause)

    def apply(command: Command, message: String, cause: Option[Throwable]): WorkflowException =
      WorkflowException(command.entityId, command.name, message, cause)

  }

  object ProtocolException {
    def apply(message: String): WorkflowException =
      WorkflowException(workflowId = "", commandName = "", "Protocol error: " + message, None)

    def apply(command: Command, message: String): WorkflowException =
      WorkflowException(command.entityId, command.name, "Protocol error: " + message, None)

    def apply(workflowId: String, message: String): WorkflowException =
      WorkflowException(workflowId, commandName = "", "Protocol error: " + message, None)

    def apply(init: WorkflowEntityInit, message: String): WorkflowException =
      ProtocolException(init.entityId, message)
  }

  def failureMessageForLog(cause: Throwable): String = cause match {
    case WorkflowException(workflowId, commandName, _, _) =>
      val workflowDescription = if (workflowId.nonEmpty) s" [$workflowId]" else ""
      s"Terminating workflow$workflowDescription due to unexpected failure for command [$commandName]"
    case _ => "Terminating workflow due to unexpected failure"
  }
}
