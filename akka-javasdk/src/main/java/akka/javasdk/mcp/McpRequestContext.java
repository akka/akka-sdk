/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.mcp;

import akka.annotation.DoNotInherit;
import akka.grpc.javadsl.Metadata;
import akka.http.javadsl.model.HttpHeader;
import akka.javasdk.JwtClaims;
import akka.javasdk.Principals;
import akka.javasdk.Tracing;

import java.util.List;
import java.util.Optional;

/** Not for user extension, can be injected as constructor parameter into MCP endpoint components */
@DoNotInherit
public interface McpRequestContext {
  /**
   * Get the principals associated with this request.
   *
   * @return The principals associated with this request.
   */
  Principals getPrincipals();

  /** @return The JWT claims, if any, associated with this request. */
  JwtClaims getJwtClaims();

  /** Access to tracing for custom app specific tracing. */
  Tracing tracing();

  /**
   * @return A header with the given name (case ignored) if present in the current request,
   *     Optional.empty() if not.
   */
  Optional<HttpHeader> requestHeader(String headerName);

  /** @return A list with all the headers of the current request */
  List<HttpHeader> allRequestHeaders();
}
