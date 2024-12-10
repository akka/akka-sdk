/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.annotation.InternalApi
import akka.javasdk.view.UpdateContext

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class ViewException(
    componentId: String,
    commandName: String,
    message: String,
    cause: Option[Throwable])
    extends RuntimeException(message, cause.orNull)

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ViewException {

  def apply(componentId: String, context: UpdateContext, message: String, cause: Option[Throwable]): ViewException =
    ViewException(componentId, context.eventName, message, cause)

}
