/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

import akka.annotation.DoNotInherit;
import akka.util.ByteString;

/**
 * HTTP client for making outbound HTTP requests to other services.
 *
 * <p>HttpClient provides a fluent API for building and executing HTTP requests with various HTTP
 * methods. It supports request customization including headers, authentication, request bodies,
 * timeouts, and retries.
 *
 * <p><strong>Basic Usage:</strong> Use the HTTP method factories ({@link #GET}, {@link #POST},
 * etc.) to start building requests, then chain configuration methods and finally call {@code
 * invoke()} to execute.
 *
 * <p><strong>Request Building:</strong> The returned {@link RequestBuilder} allows configuring
 * headers, request bodies, authentication, timeouts, retries, and response parsing before
 * execution.
 *
 * <p><strong>Response Handling:</strong> By default, responses are returned as {@code ByteString}.
 * Use {@code responseBodyAs(Class)} to automatically deserialize JSON responses to Java objects.
 *
 * <p><strong>Error Handling:</strong> HTTP error status codes (4xx, 5xx) are included in the
 * response. Use the response's status code to determine success or failure and handle errors
 * appropriately.
 *
 * <p>Not for user extension, instances provided by {@link HttpClientProvider} and the testkit.
 */
@DoNotInherit
public interface HttpClient {
  RequestBuilder<ByteString> GET(String uri);

  RequestBuilder<ByteString> POST(String uri);

  RequestBuilder<ByteString> PUT(String uri);

  RequestBuilder<ByteString> PATCH(String uri);

  RequestBuilder<ByteString> DELETE(String uri);
}
