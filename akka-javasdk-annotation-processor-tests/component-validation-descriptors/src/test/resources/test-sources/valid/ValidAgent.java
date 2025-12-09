package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "valid-agent", name = "Valid Agent", description = "A test agent")
public class ValidAgent extends Agent {

  public Effect<String> query(String request) {
    return effects().reply("response");
  }
}
