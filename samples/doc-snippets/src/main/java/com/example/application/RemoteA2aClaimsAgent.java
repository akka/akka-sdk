package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.RemoteA2aAgent;
import akka.javasdk.annotations.Component;
import java.time.Duration;

@Component(id = "remote-a2a-claims-agent")
public class RemoteA2aClaimsAgent extends Agent {

  record AgentResponse(String answer) {}

  private static final String SYSTEM_MESSAGE =
    """
    You are a helpful assistant for insurance claims.
    Use the tools of the claims agent to look up the status of a claim.
    """.stripIndent();

  // tag::a2a-agents[]
  public Effect<AgentResponse> query(String message) {
    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .a2aAgents(
        RemoteA2aAgent.named("claims") // <1>
          .withAllowedSkills("lookup-claim") // <2>
          .withTimeout(Duration.ofSeconds(30)) // <3>
      )
      .userMessage(message)
      .responseAs(AgentResponse.class)
      .thenReply();
  }
  // end::a2a-agents[]

}
