package com.example.eval;

import akka.javasdk.JsonSupport;
import akka.javasdk.agent.Decision;
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.ToolCallGuardrail;
import com.fasterxml.jackson.core.JsonProcessingException;

// tag::all[]
/** Denies a refund above the configured amount. Such a refund needs a human. */
public class RefundLimitGuard implements ToolCallGuardrail {

  private final int maxAmountCents;

  public RefundLimitGuard(GuardrailContext context) {
    this.maxAmountCents = context.config().getInt("max-amount-cents"); // <1>
  }

  @Override
  public Decision decide(CallContext ctx) {
    int amountCents = amountCents(ctx.arguments()); // <2>
    if (amountCents > maxAmountCents) {
      return new Decision.Deny(
        "a refund of " + amountCents + " cents is above the limit of " + maxAmountCents
      ); // <3>
    }
    return new Decision.Allow();
  }

  private static int amountCents(String arguments) {
    try {
      return JsonSupport.getObjectMapper().readTree(arguments).path("amountCents").asInt();
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("tool arguments are not JSON", e);
    }
  }
}
// end::all[]
