/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "agent-no-effect")
public class AgentWithoutEffectMethod extends Agent {

  // No method returning Agent.Effect or Agent.StreamEffect
  public String someMethod(String input) {
    return "hello";
  }
}
