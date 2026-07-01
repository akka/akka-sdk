/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.mcp;

import akka.javasdk.CallerSpiffeContext;
import akka.javasdk.SpiffeContext;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpTool;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.mcp.AbstractMcpEndpoint;
import com.typesafe.config.Config;

@McpEndpoint(serverName = "spiffe-test", path = "/mcp-spiffe")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class SpiffeMcpEndpoint extends AbstractMcpEndpoint {

  public record CallerInfo(String caller) {}

  private final HttpClientProvider httpClientProvider;
  private final int httpPort;

  public SpiffeMcpEndpoint(HttpClientProvider httpClientProvider, Config config) {
    this.httpClientProvider = httpClientProvider;
    this.httpPort = config.getInt("akka.javasdk.testkit.http-port");
  }

  // reports the caller SPIFFE id observed on this MCP request (empty if none)
  @McpTool(description = "reports the caller SPIFFE id observed on this request")
  public String caller() {
    return requestContext()
        .getSpiffeContext()
        .flatMap(SpiffeContext::getCallerContext)
        .map(CallerSpiffeContext::getSpiffeId)
        .orElse("");
  }

  // makes an outbound HTTP call to /spiffe/caller and returns the caller it observed; "service"
  // targets the Akka service by name (cross-service), "external" targets an http:// URL back to
  // self
  @McpTool(description = "delegate to another service and report the caller it observed")
  public String delegate(@Description("service or external") String kind) {
    var target =
        switch (kind) {
          case "service" -> "sdk-tests";
          case "external" -> "http://localhost:" + httpPort;
          default -> throw new IllegalArgumentException("Unknown kind [" + kind + "]");
        };
    return httpClientProvider
        .httpClientFor(target)
        .GET("/spiffe/caller")
        .responseBodyAs(CallerInfo.class)
        .invoke()
        .body()
        .caller();
  }
}
