/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class ViewException(componentId: String, message: String, cause: Option[Throwable])
    extends RuntimeException(message, cause.orNull)
