/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.RemoteMcpTools;
import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;
import akkajavasdk.components.agent.McpToolCallGuardrailTestAgent;

@Component(
    id = "mcp-guarded-autonomous-agent",
    description = "Test agent with an MCP tool, a tool interceptor and a tool-call guardrail.")
public class McpGuardedAutonomousAgent extends AutonomousAgent {

  public static final String REWRITTEN_ARGUMENTS = "{\"echo\":\"rewritten\"}";

  @Override
  public AgentDefinition definition() {
    return define()
        .instructions("You are a helpful assistant")
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
                        return "intercepted " + responsePayload;
                      }
                    }))
        .capability(TaskAcceptance.of(TestTasks.STRING_TASK));
  }
}
