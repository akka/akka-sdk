package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "agent-zero-args")
public class ValidAgentWithZeroArgs extends Agent {

  public Effect<String> query() {
    return effects().reply("response");
  }
}
