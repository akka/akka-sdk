/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
    id = "triage-test-agent",
    description = "Classifies support requests and hands off to the appropriate specialist.")
public class TriageTestAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
        .capability(
            TaskAcceptance.of(TestTasks.RESOLVE)
                .maxIterationsPerTask(3)
                .canHandoffTo(SpecialistTestAgent.class));
  }
}
