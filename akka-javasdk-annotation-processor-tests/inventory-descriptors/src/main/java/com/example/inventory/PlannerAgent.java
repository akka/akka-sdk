/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;

@Component(id = "planner", description = "Plans tasks")
@AgentRole("planner")
public class PlannerAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define().instructions("Plan the tasks");
  }
}
