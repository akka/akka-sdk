package com.example.eval;

import akka.javasdk.testkit.eval.EvalCase;
import java.util.List;

/** The eval cases of the order agent, shared by the quality suite and the mocked suite. */
final class OrderCases {

  private OrderCases() {}

  // tag::cases[]
  static final EvalCase<String> ORDER_STATUS = EvalCase.of(
    "order-status", // <1>
    "Where is order o_42?" // <2>
  );

  static final EvalCase<String> FULL_REFUND = EvalCase.of(
    "full-refund",
    "Order o_9 arrived broken. I want my money back."
  );

  static final EvalCase<String> GREETING = EvalCase.of("greeting", "hello?");

  static final List<EvalCase<String>> CURATED = List.of(ORDER_STATUS, FULL_REFUND, GREETING);
  // end::cases[]
}
