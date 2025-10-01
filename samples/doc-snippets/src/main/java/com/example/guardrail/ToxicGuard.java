package com.example.guardrail;

// tag::all[]
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.TextGuardrail;

public class ToxicGuard implements TextGuardrail {

  private final String searchFor;

  public ToxicGuard(GuardrailContext context) {
    searchFor = context.config().getString("search-for");
  }

  @Override
  public Result evaluate(String text) {
    // this would typically be more advanced in a real implementation
    if (text.contains(searchFor)) {
      return new Result(false, "Toxic response '%s' not allowed.".formatted(searchFor));
    } else {
      return Result.OK;
    }
  }
}
// end::all[]
