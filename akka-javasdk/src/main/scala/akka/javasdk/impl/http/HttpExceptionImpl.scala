/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import akka.annotation.InternalApi
import akka.http.scaladsl.model.StatusCode
import akka.runtime.sdk.spi.HttpEndpointInvocationException

import scala.util.control.NoStackTrace

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
