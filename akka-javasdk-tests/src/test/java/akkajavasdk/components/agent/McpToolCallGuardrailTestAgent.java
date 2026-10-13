/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Guardrail;
import akka.javasdk.agent.RemoteMcpTools;
import akka.javasdk.annotations.Component;

@Component(id = "mcp-tool-call-guardrail-test-agent")
public class McpToolCallGuardrailTestAgent extends Agent {
  public record SomeResponse(String response) {}

  public static final String MCP_ENDPOINT = "http://localhost:19391/mcp";

  public Effect<SomeResponse> ask(String question) {
    return effects()
        .systemMessage("You are a helpful assistant")
        .mcpTools(RemoteMcpTools.fromServer(MCP_ENDPOINT).withAllowedToolNames("echo"))
        .userMessage(question)
        .map(SomeResponse::new)
        .onFailure(
            cause -> {
              return switch (cause) {
                case Guardrail.GuardrailException e -> new SomeResponse(e.getMessage());
                case RuntimeException e -> throw e;
                default -> throw new RuntimeException(cause);
              };
            })
        .thenReply();
  }
}
