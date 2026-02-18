package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "evaluator-agent")
public class EvaluatorAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
    You are a critical quality assurance expert. Your job is to evaluate if the drafted \
    content sufficiently covers the requested topic.

    Compare the content against the original topic.
    - If it covers the topic well, respond with APPROVED followed by a brief note on \
      what makes it good.
    - If it misses key aspects or is too shallow, provide specific, actionable feedback \
      as a numbered list explaining what needs improvement and why.
    """;

  public Effect<String> evaluate(String message) {
    return effects().systemMessage(SYSTEM_MESSAGE).userMessage(message).thenReply();
  }
}
