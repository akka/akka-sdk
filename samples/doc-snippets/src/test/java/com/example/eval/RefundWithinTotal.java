package com.example.eval;

import akka.javasdk.testkit.eval.EvalCase;
import akka.javasdk.testkit.eval.Evaluator;
import akka.javasdk.testkit.eval.Interaction;

// tag::class[]
/** The refund the agent issued must not exceed the order's total. */
public final class RefundWithinTotal implements Evaluator {

  private final int totalCents;

  public RefundWithinTotal(int totalCents) {
    this.totalCents = totalCents;
  }

  @Override
  public String name() {
    return "refund-within-total"; // <1>
  }

  @Override
  public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
    var refund = interaction
      .toolCalls()
      .stream()
      .filter(call -> call.name().equals("issueRefund"))
      .findFirst();
    if (refund.isEmpty()) {
      return EvalResult.abstain("no refund was issued"); // <2>
    }
    var amount = ((Number) refund.get().arguments().get("amountCents")).intValue(); // <3>
    return amount <= totalCents
      ? EvalResult.pass()
      : EvalResult.fail("refunded " + amount + " cents of a " + totalCents + " cent order");
  }
}
// end::class[]
