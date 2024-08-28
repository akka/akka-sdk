/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

import akka.http.scaladsl.model.StatusCodes;
import akka.http.scaladsl.model.StatusCode;
import akka.javasdk.impl.http.HttpExceptionImpl;

public final class HttpException {

  // Static factories only
  private HttpException() {}

  public static RuntimeException badRequest() {
    throw new HttpExceptionImpl(StatusCodes.BadRequest());
  }

  public static RuntimeException badRequest(String responseText) {
    return new HttpExceptionImpl(StatusCodes.BadRequest(), responseText);
  }

  public static RuntimeException notFound() {
    throw new HttpExceptionImpl(StatusCodes.NotFound());
  }

  public static RuntimeException forbidden() {
    throw new HttpExceptionImpl(StatusCodes.Forbidden());
  }

  public static RuntimeException forbidden(String responseText) {
    return new HttpExceptionImpl(StatusCodes.Forbidden(), responseText);
  }

  public static RuntimeException unauthorized() {
    throw new HttpExceptionImpl(StatusCodes.Unauthorized());
  }

  public static RuntimeException unauthorized(String responseText) {
    return new HttpExceptionImpl(StatusCodes.Unauthorized(), responseText);
  }

  public static RuntimeException notImplemented() {
    return new HttpExceptionImpl(StatusCodes.NotImplemented());
  }

  /**
   * @return An exception with an arbitrary HTTP status code.
   *
   * Note: a large list of predefined status codes can be found in {@link akka.http.javadsl.model.StatusCodes}
   */
  public static RuntimeException error(akka.http.javadsl.model.StatusCode statusCode) {
    return new HttpExceptionImpl((StatusCode) statusCode);
  }


  /**
   * @return An exception with an arbitrary HTTP status code.
   *
   * Note: a large list of predefined status codes can be found in {@link akka.http.javadsl.model.StatusCodes}
   */
  public static RuntimeException error(akka.http.javadsl.model.StatusCode statusCode, String responseText) {
    return new HttpExceptionImpl((StatusCode) statusCode, responseText);
  }

}
