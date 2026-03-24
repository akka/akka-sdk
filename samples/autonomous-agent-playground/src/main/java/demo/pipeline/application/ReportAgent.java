/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.pipeline.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

@Component(id = "report-agent")
public class ReportAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal(
        "Process report phases: collect data, analyze findings, produce comprehensive reports."
      )
      .canAcceptTask(PipelineTasks.COLLECT)
      .canAcceptTask(PipelineTasks.ANALYZE)
      .canAcceptTask(PipelineTasks.REPORT);
  }

  @FunctionTool(description = "Collect data on a topic and return findings")
  public String collectData(String topic) {
    return "Collected data on: " + topic;
  }

  @FunctionTool(description = "Analyze data and return analysis")
  public String analyzeData(String data) {
    return "Analysis of: " + data;
  }
}
