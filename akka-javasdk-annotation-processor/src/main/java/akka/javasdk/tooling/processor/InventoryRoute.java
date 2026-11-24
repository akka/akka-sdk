/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import java.util.List;
import java.util.Optional;

/** An HTTP, gRPC or MCP route of an endpoint, with its effective ACL and JWT rule. */
record InventoryRoute(
    String protocol,
    String className,
    Optional<String> service,
    Optional<String> method,
    Optional<String> httpMethod,
    Optional<String> path,
    boolean webSocket,
    Acl acl,
    Optional<Jwt> jwt) {

  /** The effective ACL of a route. */
  record Acl(boolean denyAll, List<Matcher> allow, List<Matcher> deny, int denyCode) {}

  /** An ACL matcher, where the key is {@code principal}, {@code spiffe} or {@code service}. */
  record Matcher(String key, String value, boolean parentProject) {}

  /** The effective JWT rule of a route. */
  record Jwt(boolean bearerToken, List<String> issuers, List<Claim> staticClaims) {}

  /** A static claim of a JWT rule. */
  record Claim(String claim, List<String> values, Optional<String> pattern) {}
}
