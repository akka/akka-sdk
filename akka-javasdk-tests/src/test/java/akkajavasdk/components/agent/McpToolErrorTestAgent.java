/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import com.typesafe.config.Config;

/** Calls the failing tool of the MCP endpoint of this service. */
@Component(id = "mcp-tool-error-test-agent")
public class McpToolErrorTestAgent extends Agent {

  private final Config config;

  public McpToolErrorTestAgent(Config config) {
    this.config = config;
  }

  public Effect<String> query(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .mcpTools(RecordingMcpToolInterceptor.failingTool(config, "mcp-tool-error-test-agent"))
        .userMessage(question)
        .thenReply();
  }
}
