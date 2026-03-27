/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

@Component(id = "tool-using-agent")
public class ToolUsingAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
        .goal("Answer questions using available tools.")
        .capability(TaskAcceptance.of(TestTasks.TEST_TASK).maxIterationsPerTask(5))
        .tools(new DateService());
  }

  public static class DateService {
    @FunctionTool(description = "Get today's date")
    public String getToday() {
      return "2025-01-15";
    }
  }
}
