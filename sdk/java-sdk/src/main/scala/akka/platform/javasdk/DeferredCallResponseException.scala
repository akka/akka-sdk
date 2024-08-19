/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk

import akka.platform.javasdk.StatusCode.ErrorCode

/** Exception used when a DeferredCall fails to wrap the origin error, plus the error code associated. */
final case class DeferredCallResponseException(description: String, errorCode: ErrorCode, cause: Throwable)
    extends RuntimeException(cause)
