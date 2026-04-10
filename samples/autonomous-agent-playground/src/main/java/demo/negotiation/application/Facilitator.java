package demo.negotiation.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Moderation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(id = "facilitator", description = "Facilitates negotiations between parties")
public class Facilitator extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal(
        """
        Facilitate negotiations by directing each psarty through structured rounds \
        of offers and counteroffers to reach agreement.
        """
      )
      .capability(TaskAcceptance.of(NegotiationTasks.NEGOTIATE))
      .capability(Moderation.of(Buyer.class, Seller.class).maxRounds(10));
  }
}
