/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.mcp;

import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpPrompt;
import akka.javasdk.annotations.mcp.McpResource;
import akka.javasdk.annotations.mcp.McpTool;

public class TestMcpEndpoints {

  // Base class declaring tool/prompt/resource methods that a concrete @McpEndpoint subclass
  // inherits.
  public abstract static class BaseMcpEndpoint {

    @McpTool(name = "inherited-tool", description = "inherited tool")
    public String inheritedTool(String someParam) {
      return someParam;
    }

    @McpPrompt(name = "inherited-prompt", description = "inherited prompt")
    public String inheritedPrompt() {
      return "inherited prompt";
    }

    @McpResource(
        uri = "file:///inherited",
        name = "inherited-resource",
        description = "inherited resource")
    public String inheritedResource() {
      return "inherited resource";
    }

    @McpTool(name = "overridden-tool", description = "base")
    public String overriddenTool() {
      return "base";
    }

    @McpTool(name = "dropped-tool", description = "base")
    public String droppedTool() {
      return "base";
    }
  }

  @McpEndpoint(serverName = "inheriting-server", serverVersion = "1.0")
  public static class InheritingMcpEndpoint extends BaseMcpEndpoint {

    @McpTool(name = "own-tool", description = "own tool")
    public String ownTool() {
      return "own";
    }

    // Re-declares @McpTool — should keep the inherited tool with the override implementation.
    @Override
    @McpTool(name = "overridden-tool", description = "override")
    public String overriddenTool() {
      return "override";
    }

    // No annotation on the override — the tool from the base class is opted out.
    @Override
    public String droppedTool() {
      return "override";
    }
  }
}
