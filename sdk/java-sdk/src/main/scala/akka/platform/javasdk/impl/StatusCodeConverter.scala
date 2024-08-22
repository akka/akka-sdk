/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import akka.annotation.InternalApi
import io.grpc.Status
import akka.http.javadsl.model.{ StatusCode => HttpStatusCode, StatusCodes => HttpStatusCodes }

/**
 * INTERNAL API
 */
@InternalApi
object StatusCodeConverter {
  def toGrpcCode(statusCode: HttpStatusCode): Status.Code = {
    statusCode match {
      case HttpStatusCodes.BAD_REQUEST           => Status.Code.INVALID_ARGUMENT
      case HttpStatusCodes.UNAUTHORIZED          => Status.Code.UNAUTHENTICATED
      case HttpStatusCodes.FORBIDDEN             => Status.Code.PERMISSION_DENIED
      case HttpStatusCodes.NOT_FOUND             => Status.Code.NOT_FOUND
      case HttpStatusCodes.GATEWAY_TIMEOUT       => Status.Code.DEADLINE_EXCEEDED
      case HttpStatusCodes.CONFLICT              => Status.Code.ALREADY_EXISTS
      case HttpStatusCodes.TOO_MANY_REQUESTS     => Status.Code.RESOURCE_EXHAUSTED
      case HttpStatusCodes.INTERNAL_SERVER_ERROR => Status.Code.INTERNAL
      case HttpStatusCodes.SERVICE_UNAVAILABLE   => Status.Code.UNAVAILABLE
      case _                                     => Status.Code.INTERNAL
    }
  }

}
