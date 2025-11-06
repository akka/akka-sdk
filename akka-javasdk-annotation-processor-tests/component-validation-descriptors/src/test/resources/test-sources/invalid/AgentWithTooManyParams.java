package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "agent-too-many-params")
public class AgentWithTooManyParams extends Agent {

  public Effect<String> query(String param1, String param2, String param3) {
    // Invalid: has more than one parameter
    return effects().reply("response");
  }
}
