package com.example.application;

// tag::class[]
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "my-agent") // <2>
public class MyAgent extends Agent { // <1>

  public Effect<String> query(String question) { // <3>
    return effects().systemMessage("You are a helpful...").userMessage(question).thenReply();
  }
}
// end::class[]
