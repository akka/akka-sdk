package com.example.guardrail;

// tag::all[]
import akka.javasdk.agent.ClassifierClient;
import akka.javasdk.agent.Decision;
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.ModelGuardrail;

public class ClassifierBackedGuard implements ModelGuardrail {

  private final ClassifierClient classifierClient;
  private final String classifierName;

  public ClassifierBackedGuard(GuardrailContext context) {
    this.classifierClient = context.classifierClient(); // <1>
    this.classifierName = context.config().getString("classifier"); // <2>
  }

  @Override
  public Decision decide(CallContext ctx) {
    try {
      var classification = classifierClient.classify(classifierName, ctx.text()); // <3>
      if (classification.label().map("toxic"::equals).orElse(false)) {
        return new Decision.Deny("blocked by classifier " + classifierName);
      }
      return new Decision.Allow();
    } catch (RuntimeException e) {
      return new Decision.Fail("classifier invocation failed", e); // <4>
    }
  }
}
// end::all[]
