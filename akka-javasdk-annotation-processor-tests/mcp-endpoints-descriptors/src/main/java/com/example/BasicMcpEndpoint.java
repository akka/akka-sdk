/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpTool;

@McpEndpoint(
    serverName = "my-mcp-server",
    serverVersion = "1.0",
    instructions = "Some special care is needed when using this example mcp endpoint")
class BasicMcpEndpoint {

  public BasicMcpEndpoint() {}

  @McpTool(name = "echo", description = "Echoes back whatever string is sent to it")
  public String echoTool(String someParam) {
    return someParam;
  }
}
