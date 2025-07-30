/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

import akka.http.scaladsl.model.StatusCode;
import akka.http.scaladsl.model.StatusCodes;
import akka.javasdk.impl.http.HttpExceptionImpl;

/**
 * Factory class for creating HTTP exceptions that result in specific HTTP error responses.
 *
 * <p>HttpException provides static factory methods for creating exceptions that, when thrown from
 * HTTP endpoint methods, are automatically converted to appropriate HTTP error responses with the
 * corresponding status codes.
 *
 * <p><strong>Common Error Responses:</strong>
 *
 * <ul>
 *   <li>{@link #badRequest()} - 400 Bad Request for invalid client input
 *   <li>{@link #unauthorized()} - 401 Unauthorized for authentication failures
 *   <li>{@link #forbidden()} - 403 Forbidden for authorization failures
 *   <li>{@link #notFound()} - 404 Not Found for missing resources
 *   <li>{@link #notImplemented()} - 501 Not Implemented for unsupported operations
 * </ul>
 *
 * <p><strong>Custom Status Codes:</strong> Use {@link #error(akka.http.javadsl.model.StatusCode)}
 * for arbitrary HTTP status codes not covered by the predefined factory methods.
 *
 * <p><strong>Error Messages:</strong> Most factory methods have overloads that accept a response
 * text parameter to provide additional error details to the client.
 *
 * <p><strong>Alternative Error Handling:</strong>
 *
 * <ul>
 *   <li>{@code IllegalArgumentException} is automatically converted to 400 Bad Request
 *   <li>Other exceptions become 500 Internal Server Error
 *   <li>Return {@link HttpResponses} error methods for more control
 * </ul>
 */
public final class HttpException {

  // Static factories only
  private HttpException() {}

  public static RuntimeException badRequest() {
    return new HttpExceptionImpl(StatusCodes.BadRequest());
  }

  public static RuntimeException badRequest(String responseText) {
    return new HttpExceptionImpl(StatusCodes.BadRequest(), responseText);
  }

  public static RuntimeException notFound() {
    return new HttpExceptionImpl(StatusCodes.NotFound());
  }

  public static RuntimeException forbidden() {
    return new HttpExceptionImpl(StatusCodes.Forbidden());
  }

  public static RuntimeException forbidden(String responseText) {
    return new HttpExceptionImpl(StatusCodes.Forbidden(), responseText);
  }

  public static RuntimeException unauthorized() {
    return new HttpExceptionImpl(StatusCodes.Unauthorized());
  }

  public static RuntimeException unauthorized(String responseText) {
    return new HttpExceptionImpl(StatusCodes.Unauthorized(), responseText);
  }

  public static RuntimeException notImplemented() {
    return new HttpExceptionImpl(StatusCodes.NotImplemented());
  }

  /**
   * @return An exception with an arbitrary HTTP status code.
   *     <p>Note: a large list of predefined status codes can be found in {@link
   *     akka.http.javadsl.model.StatusCodes}
   */
  public static RuntimeException error(akka.http.javadsl.model.StatusCode statusCode) {
    return new HttpExceptionImpl((StatusCode) statusCode);
  }

  /**
   * @return An exception with an arbitrary HTTP status code.
   *     <p>Note: a large list of predefined status codes can be found in {@link
   *     akka.http.javadsl.model.StatusCodes}
   */
  public static RuntimeException error(
      akka.http.javadsl.model.StatusCode statusCode, String responseText) {
    return new HttpExceptionImpl((StatusCode) statusCode, responseText);
  }
}
