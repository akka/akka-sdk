/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;

@Component(id = "role-agent", name = "Role Agent", description = "Answers questions")
@AgentRole("helper")
public class RoleAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects().reply("answer");
  }
}
