package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentTeam;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Content refinement agent that coordinates a writer and critic in an
 * iterative revision loop until the content is approved.
 */
@Component(id = "content-refinement-agent")
public class ContentRefinementAgent extends Agent implements AgentTeam<String, String> {

  private static final Logger logger = LoggerFactory.getLogger(ContentRefinementAgent.class);

  private final ComponentClient componentClient;

  public ContentRefinementAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public String instructions() {
    return """
    You coordinate a content creation and refinement process.

    Follow this loop:
    1. Hand off to the writer agent with the user's brief
    2. Hand off to the critic agent with the writer's output
    3. If the critic responds with APPROVED, you are done — compile the final result
    4. If the critic has feedback, hand off to the writer again with both \
       the current content and the critic's feedback
    5. Repeat steps 2-4

    Track each revision cycle. When compiling the final result, include:
    - The final approved content
    - A summary of each revision (what feedback was given, what changed)
    """;
  }

  @Override
  public List<Delegation> delegations() {
    return List.of(
      Delegation.toAgent(WriterAgent.class).withDescription(
        "Writes or revises content based on a brief and optional feedback from a critic"
      ),
      Delegation.toAgent(CriticAgent.class).withDescription(
        "Reviews content for quality, clarity, accuracy, and tone. Returns feedback or APPROVED"
      )
    );
  }

  @Override
  public Class<String> resultType() {
    return String.class;
  }
}
