/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.ClassifierClient;
import akka.javasdk.agent.Decision;
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.ModelGuardrail;
import java.util.concurrent.CompletionStage;

/**
 * A ModelGuardrail that delegates the decision to a configured classifier, resolved via {@link
 * GuardrailContext#classifierClient()}. Demonstrates the "used inline from a guardrail" scenario.
 *
 * <p>The classifier name comes from the guardrail's own config section ({@code classifier = ...})
 * rather than a string literal, so the guardrail-to-classifier binding can be re-pointed at
 * deployment time without rebuilding -- the canonical pattern for governance-owned names.
 *
 * <p>Overrides the async {@link #decideAsync} variant (the poweruser shape), composing {@link
 * ClassifierClient#classifyAsync} without blocking and mapping a classifier failure to an explicit
 * {@link Decision.Fail}, exercising that combination end-to-end.
 */
public class ClassifierBackedModelGuard implements ModelGuardrail {
  private final ClassifierClient classifierClient;
  private final String classifierName;

  public ClassifierBackedModelGuard(GuardrailContext context) {
    this.classifierClient = context.classifierClient();
    this.classifierName = context.config().getString("classifier");
  }

  @Override
  public Decision decide(CallContext ctx) {
    throw new UnsupportedOperationException(
        "sync decide must not be called, decideAsync is overridden");
  }

  @Override
  public CompletionStage<Decision> decideAsync(CallContext ctx) {
    return classifierClient
        .classifyAsync(classifierName, ctx.text())
        .thenApply(
            classification ->
                classification
                    .label()
                    .filter("toxic"::equals)
                    .<Decision>map(label -> new Decision.Deny("blocked by classifier: " + label))
                    .orElseGet(Decision.Allow::new))
        .exceptionally(e -> new Decision.Fail("classifier invocation failed", e));
  }
}
