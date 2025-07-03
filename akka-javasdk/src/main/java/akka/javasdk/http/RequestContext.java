/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

import akka.annotation.DoNotInherit;
import akka.http.javadsl.model.HttpHeader;
import akka.javasdk.Context;
import akka.javasdk.JwtClaims;
import akka.javasdk.Principals;
import akka.javasdk.Tracing;

import java.util.List;
import java.util.Optional;

/**
 * Context information available during HTTP endpoint request processing.
 *
 * <p>Provides access to request metadata including headers, query parameters, authentication
 * information, and tracing capabilities for HTTP endpoint methods. This context is available during
 * the processing of HTTP requests and provides essential information for request handling.
 *
 * <p><strong>Access Methods:</strong>
 *
 * <ul>
 *   <li>Extend {@link AbstractHttpEndpoint} and use {@code requestContext()}
 *   <li>Inject as constructor parameter into HTTP endpoint classes
 * </ul>
 *
 * <p><strong>Request Headers:</strong> Access HTTP headers via {@link #requestHeader(String)} for
 * specific headers or {@link #allRequestHeaders()} for all headers. Header names are
 * case-insensitive.
 *
 * <p><strong>Query Parameters:</strong> Use {@link #queryParams()} to access URL query parameters
 * with type-safe getters for common types like strings, integers, and booleans.
 *
 * <p><strong>Authentication & Authorization:</strong> Use {@link #getPrincipals()} and {@link
 * #getJwtClaims()} to access authentication information for custom authorization logic. HTTP
 * endpoints support ACL annotations and JWT validation.
 *
 * <p><strong>Tracing:</strong> Access custom tracing capabilities via {@link #tracing()} for
 * observability and debugging.
 *
 * <p>Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface RequestContext extends Context {

  /**
   * Get the principals associated with this request.
   *
   * @return The principals associated with this request.
   */
  Principals getPrincipals();

  /** @return The JWT claims, if any, associated with this request. */
  JwtClaims getJwtClaims();

  /**
   * @return A header with the given name (case ignored) if present in the current request,
   *     Optional.empty() if not.
   */
  Optional<HttpHeader> requestHeader(String headerName);

  /** @return A list with all the headers of the current request */
  List<HttpHeader> allRequestHeaders();

  /** Access to tracing for custom app specific tracing. */
  Tracing tracing();

  /** @return The query parameters of the current request. */
  QueryParams queryParams();
}
