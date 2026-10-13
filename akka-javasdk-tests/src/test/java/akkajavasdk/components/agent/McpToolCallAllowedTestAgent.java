/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.RemoteMcpTools;
import akka.javasdk.annotations.Component;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component(id = "mcp-tool-call-allowed-test-agent")
public class McpToolCallAllowedTestAgent extends Agent {
  public record SomeResponse(String response) {}

  public static final String REWRITTEN_ARGUMENTS = "{\"echo\":\"rewritten\"}";

  public static final AtomicInteger toolResponses = new AtomicInteger();
  public static final AtomicReference<String> responseArguments = new AtomicReference<>();

  public Effect<SomeResponse> ask(String question) {
    return effects()
        .systemMessage("You are a helpful assistant")
        .mcpTools(
            RemoteMcpTools.fromServer(McpToolCallGuardrailTestAgent.MCP_ENDPOINT)
                .withAllowedToolNames("echo")
                .withToolInterceptor(
                    new RemoteMcpTools.ToolInterceptor() {
                      @Override
                      public String interceptRequest(
                          RemoteMcpTools.ToolInterceptorContext context,
                          String requestPayloadJson) {
                        return REWRITTEN_ARGUMENTS;
                      }

                      @Override
                      public String interceptResponse(
                          RemoteMcpTools.ToolInterceptorContext context,
                          String requestPayloadJson,
                          String responsePayload) {
                        toolResponses.incrementAndGet();
                        responseArguments.set(requestPayloadJson);
                        return "intercepted " + responsePayload;
                      }
                    }))
        .userMessage(question)
        .map(SomeResponse::new)
        .thenReply();
  }
}
