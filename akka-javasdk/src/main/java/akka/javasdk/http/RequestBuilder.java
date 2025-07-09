/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

import akka.annotation.DoNotInherit;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.pattern.RetrySettings;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * A builder for HTTP requests and handling of their responses
 *
 * <p>Not for user extension, use {@link HttpClient} to get an instance
 *
 * @param <R> The type the response body will be parsed into
 */
@DoNotInherit
public interface RequestBuilder<R> {

  RequestBuilder<R> withRequest(HttpRequest request);

  RequestBuilder<R> addHeader(String header, String value);

  RequestBuilder<R> addHeader(HttpHeader header);

  RequestBuilder<R> withHeaders(Iterable<HttpHeader> headers);

  RequestBuilder<R> addCredentials(HttpCredentials credentials);

  RequestBuilder<R> withTimeout(Duration timeout);

  RequestBuilder<R> addQueryParameter(String key, String value);

  /**
   * Transform the request before sending it. This method allows for extra request configuration.
   */
  RequestBuilder<R> modifyRequest(Function<HttpRequest, HttpRequest> adapter);

  /**
   * Prepare for sending an HTTP request with an application/json body. The passed Object is
   * serialized to json using the application's default Jackson serializer.
   */
  RequestBuilder<R> withRequestBody(Object object);

  /** Prepare for sending an HTTP request with a text/plain body. */
  RequestBuilder<R> withRequestBody(String text);

  /** Prepare for sending an HTTP request with an application/octet-stream body. */
  RequestBuilder<R> withRequestBody(byte[] bytes);

  /**
   * Prepare for sending an HTTP request with an arbitrary payload encoded as described by the
   * content type
   */
  RequestBuilder<R> withRequestBody(ContentType type, byte[] bytes);

  /**
   * Set the retry settings for this call.
   *
   * @param retrySettings The retry settings
   */
  RequestBuilder<R> withRetry(RetrySettings retrySettings);

  /**
   * Set the retry settings for this call. A predefined backoff strategy will be calculated based on
   * the number of maxRetries.
   *
   * @param maxRetries The number of retries to make
   */
  RequestBuilder<R> withRetry(int maxRetries);

  CompletionStage<StrictResponse<R>> invokeAsync();

  StrictResponse<R> invoke();

  /**
   * Converts the response body to the specified type.
   *
   * <p>The response body payload is expected to be a JSON object and will be deserialized to the
   * specified type using the application's default Jackson deserializer.
   *
   * @param type the expected class type of the response body
   * @return a RequestBuilder configured to produce a StrictResponse with a deserialized response
   *     body of type T
   */
  <T> RequestBuilder<T> responseBodyAs(Class<T> type);

  /**
   * Converts the response body as a list of the specified type.
   *
   * <p>The response body payload is expected to be a JSON array and each element will be
   * deserialized to the specified type using the application's default Jackson deserializer.
   *
   * @param elementType the expected class type of the response body
   * @return a RequestBuilder configured to produce a StrictResponse with a deserialized response
   *     body of type T
   */
  <T> RequestBuilder<List<T>> responseBodyAsListOf(Class<T> elementType);

  /**
   * Converts the response body to the specified type using the provided parser function.
   *
   * @param parse the function to parse the response body
   * @return a RequestBuilder configured to produce a StrictResponse with a deserialized response
   *     body of type T
   */
  <T> RequestBuilder<T> parseResponseBody(Function<byte[], T> parse);
}
