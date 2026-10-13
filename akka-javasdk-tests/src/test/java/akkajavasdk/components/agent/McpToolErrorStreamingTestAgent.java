/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import com.typesafe.config.Config;

/** Streams a reply after it calls the failing tool of the MCP endpoint of this service. */
@Component(id = "mcp-tool-error-streaming-test-agent")
public class McpToolErrorStreamingTestAgent extends Agent {

  private final Config config;

  public McpToolErrorStreamingTestAgent(Config config) {
    this.config = config;
  }

  public StreamEffect ask(String question) {
    return streamEffects()
        .systemMessage("You are a helpful...")
        .mcpTools(
            RecordingMcpToolInterceptor.failingTool(config, "mcp-tool-error-streaming-test-agent"))
        .userMessage(question)
        .thenReply();
  }
}
