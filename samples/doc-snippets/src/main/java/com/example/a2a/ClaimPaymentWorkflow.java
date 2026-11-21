package com.example.a2a;

import akka.Done;
import akka.javasdk.a2a.A2aClient;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.agent.Guardrail;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

@Component(id = "claim-payment")
public class ClaimPaymentWorkflow extends Workflow<ClaimPaymentWorkflow.State> {

  public record State(String claimId, String outcome) {}

  private final A2aClient a2aClient;

  public ClaimPaymentWorkflow(A2aClient a2aClient) {
    this.a2aClient = a2aClient;
  }

  public Effect<Done> start(String claimId) {
    return effects()
      .updateState(new State(claimId, null))
      .transitionTo(ClaimPaymentWorkflow::requestRefund)
      .thenReply(Done.getInstance());
  }

  // tag::guardrail[]
  private StepEffect requestRefund() {
    var claimId = currentState().claimId();
    try {
      a2aClient
        .forAgent("billing")
        .send("refund", A2aMessage.user("Refund claim " + claimId))
        .invoke(); // <1>
      return stepEffects().updateState(new State(claimId, "refunded")).thenEnd();
    } catch (Guardrail.GuardrailException e) { // <2>
      return stepEffects()
        .updateState(new State(claimId, "needs approval: " + e.getMessage()))
        .thenEnd();
    }
  }
  // end::guardrail[]
}
