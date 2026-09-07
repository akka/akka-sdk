package com.example.eval;

import akka.javasdk.testkit.ToolCall;
import akka.javasdk.testkit.eval.EvalCase;
import akka.javasdk.testkit.eval.Evaluator;
import akka.javasdk.testkit.eval.Interaction;
import java.util.List;

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
  public Finding evaluate(
    EvalCase evalCase,
    Interaction interaction,
    List<ToolCall> toolCalls
  ) {
    var refund = toolCalls
      .stream()
      .filter(call -> call.name().equals("issueRefund"))
      .findFirst();
    if (refund.isEmpty()) {
      return Finding.abstain("no refund was issued"); // <2>
    }
    var amount = ((Number) refund.get().arguments().get("amountCents")).intValue(); // <3>
    return amount <= totalCents
      ? Finding.pass()
      : Finding.fail("refunded " + amount + " cents of a " + totalCents + " cent order");
  }
}
// end::class[]
