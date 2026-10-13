/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;
import akkajavasdk.components.agent.RecordingMcpToolInterceptor;
import com.typesafe.config.Config;

/** Calls the failing tool of the MCP endpoint of this service. */
@Component(
    id = "mcp-tool-error-autonomous-agent",
    description = "Test agent for MCP tool error results.")
public class McpToolErrorAutonomousTestAgent extends AutonomousAgent {

  private final Config config;

  public McpToolErrorAutonomousTestAgent(Config config) {
    this.config = config;
  }

  @Override
  public AgentDefinition definition() {
    return define()
        .capability(TaskAcceptance.of(TestTasks.STRING_TASK).maxIterationsPerTask(5))
        .mcpTools(
            RecordingMcpToolInterceptor.failingTool(config, "mcp-tool-error-autonomous-agent"));
  }
}
