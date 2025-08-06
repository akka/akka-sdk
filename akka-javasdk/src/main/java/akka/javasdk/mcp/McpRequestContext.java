/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.mcp;

import akka.annotation.DoNotInherit;
import akka.http.javadsl.model.HttpHeader;
import akka.javasdk.JwtClaims;
import akka.javasdk.Principals;
import akka.javasdk.Tracing;
import java.util.List;
import java.util.Optional;

/**
 * Context information available during MCP endpoint request processing.
 *
 * <p>Provides access to request metadata including headers, authentication information, and tracing
 * capabilities for MCP endpoint methods. This context is available during the processing of MCP
 * tool calls, resource requests, and prompt requests.
 *
 * <p><strong>Access Methods:</strong>
 *
 * <ul>
 *   <li>Extend {@link AbstractMcpEndpoint} and use {@code requestContext()}
 *   <li>Inject as constructor parameter into MCP endpoint classes
 * </ul>
 *
 * <p><strong>Authentication & Authorization:</strong> Use {@link #getPrincipals()} and {@link
 * #getJwtClaims()} to access authentication information for custom authorization logic. MCP
 * endpoints support ACL annotations and JWT validation.
 *
 * <p><strong>Custom Headers:</strong> Access request headers via {@link #requestHeader(String)} for
 * custom authentication schemes or client-specific metadata.
 *
 * <p>Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface McpRequestContext {
  /**
   * Get the principals associated with this request.
   *
   * @return The principals associated with this request.
   */
  Principals getPrincipals();

  /**
   * @return The JWT claims, if any, associated with this request.
   */
  JwtClaims getJwtClaims();

  /** Access to tracing for custom app specific tracing. */
  Tracing tracing();

  /**
   * @return A header with the given name (case ignored) if present in the current request,
   *     Optional.empty() if not.
   */
  Optional<HttpHeader> requestHeader(String headerName);

  /**
   * @return A list with all the headers of the current request
   */
  List<HttpHeader> allRequestHeaders();
}
