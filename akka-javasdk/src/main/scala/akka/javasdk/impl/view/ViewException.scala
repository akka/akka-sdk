/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.javasdk.view.UpdateContext

/**
 * INTERNAL API
 */
private[impl] final case class ViewException(
    viewId: String,
    commandName: String,
    message: String,
    cause: Option[Throwable])
    extends RuntimeException(message, cause.orNull)

/**
 * INTERNAL API
 */
private[impl] object ViewException {
  def apply(message: String): ViewException =
    ViewException(viewId = "", commandName = "", message, None)

  def apply(viewId: String, context: UpdateContext, message: String, cause: Option[Throwable]): ViewException =
    ViewException(viewId, context.eventName, message, cause)

}
