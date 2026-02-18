package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(
  id = "writer-agent",
  name = "Writer Agent",
  description = "Writes or revises content based on a brief and optional feedback from a critic."
)
public class WriterAgent extends Agent {

  public Effect<String> write(String message) {
    return effects()
      .systemMessage(
        """
        You are a skilled content writer. When given a brief, produce high-quality content.
        When given feedback from a critic alongside existing content, revise the content
        to address each point of feedback while preserving what was already good.
        Always return the full revised content, not just the changes."""
      )
      .userMessage(message)
      .thenReply();
  }
}
