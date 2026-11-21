/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.a2a.A2aSkill;

@Component(
    id = "skill-autonomous-agent",
    name = "Autonomous skill",
    description = "An autonomous agent that must not be published")
@A2aSkill(id = "research")
public class A2aSkillOnAutonomousAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define().instructions("Research the topic.");
  }
}
