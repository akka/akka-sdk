/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(id = "worker-agent")
public class WorkerAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
        .goal("Research a topic and produce factual findings.")
        .capability(TaskAcceptance.of(TestTasks.FINDINGS).maxIterationsPerTask(3));
  }
}
