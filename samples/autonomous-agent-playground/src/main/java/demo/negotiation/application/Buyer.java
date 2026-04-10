package demo.negotiation.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "buyer", description = "Negotiates from the buyer's perspective")
public class Buyer extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define().goal("Negotiate the best possible deal from the buyer's perspective.");
  }
}
