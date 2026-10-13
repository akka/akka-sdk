/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.RemoteMcpTools;
import akka.javasdk.annotations.Component;

@Component(id = "mcp-tool-call-no-interceptor-test-agent")
public class McpToolCallNoInterceptorTestAgent extends Agent {
  public record SomeResponse(String response) {}

  public Effect<SomeResponse> ask(String question) {
    return effects()
        .systemMessage("You are a helpful assistant")
        .mcpTools(
            RemoteMcpTools.fromServer(McpToolCallGuardrailTestAgent.MCP_ENDPOINT)
                .withAllowedToolNames("echo"))
        .userMessage(question)
        .map(SomeResponse::new)
        .thenReply();
  }
}
