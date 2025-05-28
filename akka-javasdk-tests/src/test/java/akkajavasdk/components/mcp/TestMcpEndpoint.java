/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.mcp;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.mcp.Description;
import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpResource;
import akka.javasdk.annotations.mcp.McpTool;

@McpEndpoint(serverName = "test")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TestMcpEndpoint {

  @McpTool(description = "A method that returns what is fed to it")
  public String echo(@Description("the string to echo") String echo) {
    return echo;
  }

  // FIXME allos name to be inferred from method?
  @McpResource(name = "exampletxt", uri = "file:///example.txt", mimeType = "text/plain", description = "a sample text file")
  public String exampleTxt() {
    return "This is an example resource";
  }
}
