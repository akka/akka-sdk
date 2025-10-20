package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "agent-with-stream")
public class ValidAgentWithStreamEffect extends Agent {

  public StreamEffect stream(String request) {
    return effects().reply("response");
  }
}
