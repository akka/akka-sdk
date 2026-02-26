package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentTeam;
import akka.javasdk.annotations.Component;

import java.util.List;

/**
 * Language triage agent that detects the language of an incoming request
 * and delegates to the appropriate language-specific agent.
 */
@Component(id = "language-triage-agent")
public class LanguageTriageAgent extends Agent implements AgentTeam<String, String> {

  @Override
  public String instructions() {
    return """
        Detect the language of the user's request and hand off to the appropriate agent.
        - If the request is in Spanish, hand off to the Spanish agent.
        - If the request is in English or any other language, hand off to the English agent.
        """;
  }

  @Override
  public List<Delegation> delegations() {
    return List.of(
        Delegation.toAgent(EnglishAgent.class)
            .withDescription("Handles requests in English"),
        Delegation.toAgent(SpanishAgent.class)
            .withDescription("Handles requests in Spanish")
    );
  }

  @Override
  public Class<String> resultType() {
    return String.class;
  }

  @Override
  public int maxTurns() {
    return 3;
  }
}
