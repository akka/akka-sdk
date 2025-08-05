/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import scala.util.control.NoStackTrace

import akka.annotation.InternalApi
import akka.http.scaladsl.model.StatusCode
import akka.runtime.sdk.spi.HttpEndpointInvocationException

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class HttpExceptionImpl(val statusCode: StatusCode, val responseText: Option[String])
    extends RuntimeException
    with HttpEndpointInvocationException
    with NoStackTrace {

  def this(statusCode: StatusCode) = this(statusCode, None)
  def this(statusCode: StatusCode, responseText: String) = this(statusCode, Some(responseText))

}
