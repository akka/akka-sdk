package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(
    id = "critic-agent",
    name = "Critic Agent",
    description = """
        Reviews content for quality, clarity, accuracy, and tone.
        Returns specific, actionable feedback or approves the content.""")
public class CriticAgent extends Agent {

  public Effect<String> review(String message) {
    return effects()
        .systemMessage("""
            You are a demanding but fair content critic. Review the provided content for:
            - Clarity and readability
            - Accuracy and completeness
            - Tone and engagement
            - Structure and flow

            If the content meets professional standards, respond with exactly: APPROVED
            followed by a brief note on what makes it good.

            If improvements are needed, provide specific, actionable feedback as a numbered
            list. Be constructive — explain what to fix and why.""")
        .userMessage(message)
        .thenReply();
  }
}
