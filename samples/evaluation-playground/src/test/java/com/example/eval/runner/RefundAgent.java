package com.example.eval.runner;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

/**
 * Stands in for the project's own agent. This is the system being evaluated.
 *
 * <p>It knows nothing about experiments or evaluators. An adapter that only worked against an
 * agent built to be evaluated would prove very little.
 */
@Component(id = "refund-agent")
public class RefundAgent extends Agent {

  public Effect<String> respond(String question) {
    return effects()
      .systemMessage("You answer questions about the refund policy.")
      .userMessage(question)
      .thenReply();
  }
}
