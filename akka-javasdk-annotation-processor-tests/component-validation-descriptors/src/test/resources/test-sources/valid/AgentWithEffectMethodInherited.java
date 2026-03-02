/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

class MyAgent extends Agent {
  public Effect<String> effectMethod() {
    return effects().reply("ok");
  }
}

@Component(id = "agent-no-effect")
public class AgentWithEffectMethodInherited extends MyAgent {

  // No method returning Agent.Effect or Agent.StreamEffect
  public String someMethod(String input) {
    return "hello";
  }
}
