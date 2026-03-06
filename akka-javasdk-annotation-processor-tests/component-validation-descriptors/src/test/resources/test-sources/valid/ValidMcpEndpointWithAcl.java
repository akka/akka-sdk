/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpTool;

@McpEndpoint(serverName = "test")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ValidMcpEndpointWithAcl {

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  @McpTool(description = "a test tool")
  public String myTool() {
    return "ok";
  }
}
