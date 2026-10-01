package com.example.eval;

import akka.javasdk.testkit.eval.EvalCase;
import akka.javasdk.testkit.eval.EvalLabel;
import akka.javasdk.testkit.eval.Evaluator;
import akka.javasdk.testkit.eval.Interaction;

// tag::class[]
/** The stub order system has no refund at the end of the turn. */
@EvalLabel("no-refund-issued")
public final class NoRefundIssued implements Evaluator {

  private final StubOrderService orders;

  public NoRefundIssued(StubOrderService orders) {
    this.orders = orders;
  }

  @Override
  public EvalResult evaluate(EvalCase<?> evalCase, Interaction interaction) {
    var issued = orders.issued(); // <1>
    return issued.isEmpty()
      ? EvalResult.pass()
      : EvalResult.fail("the shop refunded " + issued);
  }
}
// end::class[]
