package com.example.guardrail;

// tag::all[]
import akka.javasdk.agent.Decision;
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.ModelGuardrail;

public class ProfanityModelGuard implements ModelGuardrail { // <1>

  private final String searchFor;

  public ProfanityModelGuard(GuardrailContext context) { // <2>
    this.searchFor = context.config().getString("search-for");
  }

  @Override
  public Decision decide(CallContext ctx) { // <3>
    if (ctx.text().contains(searchFor)) {
      return new Decision.Deny("Response contained '%s'.".formatted(searchFor)); // <4>
    }
    return new Decision.Allow(); // <5>
  }
}
// end::all[]
