package demo.negotiation.application;

import akka.javasdk.agent.task.Task;

public class NegotiationTasks {

  public record NegotiationResult(String topic, String outcome, String finalOffer) {}

  public static final Task<NegotiationResult> NEGOTIATE = Task.define("Negotiate")
    .description("Facilitate a negotiation between buyer and seller.")
    .resultConformsTo(NegotiationResult.class);
}
