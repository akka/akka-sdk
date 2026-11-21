package assistant.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.RemoteA2aAgent;
import akka.javasdk.annotations.Component;

@Component(id = "claims-assistant")
public class ClaimsAssistantAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects()
      .systemMessage(
        "You help customers with their insurance claims. " +
        "Ask the claims agent for the status of a claim and answer in one sentence."
      )
      .a2aAgents(
        RemoteA2aAgent.named("claims").withAllowedSkills("claim-status") // <1> // <2>
      )
      .userMessage(question)
      .thenReply();
  }
}
