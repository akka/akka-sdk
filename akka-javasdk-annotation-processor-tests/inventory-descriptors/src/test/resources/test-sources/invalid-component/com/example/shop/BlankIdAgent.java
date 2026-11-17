package com.example.shop;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = " ")
public class BlankIdAgent extends Agent {

  public Effect<String> query(String request) {
    return effects().reply("response");
  }
}
