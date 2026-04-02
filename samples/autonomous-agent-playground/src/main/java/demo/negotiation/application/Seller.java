package demo.negotiation.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "seller", description = "Negotiates from the seller's perspective")
public class Seller extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define().goal("Negotiate the best possible deal from the seller's perspective.");
  }
}
