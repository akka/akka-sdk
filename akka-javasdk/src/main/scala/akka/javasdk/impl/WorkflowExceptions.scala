/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object WorkflowExceptions {

  final case class WorkflowException(workflowId: String, commandName: String, message: String, cause: Option[Throwable])
      extends RuntimeException(s"exception while processing [$commandName]: " + message, cause.orNull) {
    def this(workflowId: String, commandName: String, message: String) =
      this(workflowId, commandName, message, None)
  }

  object WorkflowException {

    def apply(message: String, cause: Option[Throwable]): WorkflowException =
      WorkflowException(workflowId = "", commandName = "", message, cause)

  }

}
