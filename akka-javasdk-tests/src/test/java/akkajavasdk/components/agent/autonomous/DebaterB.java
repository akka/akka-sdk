/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "debater-b", description = "Argues against the assigned position.")
public class DebaterB extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define();
  }
}
