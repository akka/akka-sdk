/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Classifier;
import akka.javasdk.agent.Decision;
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.ModelGuardrail;

/**
 * A ModelGuardrail that delegates the decision to a configured classifier, resolved via {@link
 * GuardrailContext#classifierClient()}. Demonstrates the "used inline from a guardrail" scenario.
 *
 * <p>The classifier name comes from the guardrail's own config section ({@code classifier = ...})
 * rather than a string literal, so the guardrail-to-classifier binding can be re-pointed at
 * deployment time without rebuilding -- the canonical pattern for governance-owned names.
 *
 * <p>decide(...) is still synchronous (a known limitation, see ToolGuardrail's FIXME), so this uses
 * the blocking {@link Classifier#classify}, which itself completes asynchronously, exercising that
 * combination.
 */
public class ClassifierBackedModelGuard implements ModelGuardrail {
  private final Classifier classifier;

  public ClassifierBackedModelGuard(GuardrailContext context) {
    this.classifier =
        context.classifierClient().classifier(context.config().getString("classifier"));
  }

  @Override
  public Decision decide(CallContext ctx) {
    try {
      var classification = classifier.classify(ctx.text());
      if (classification.label().map(l -> l.equals("toxic")).orElse(false)) {
        return new Decision.Deny("blocked by classifier: " + classification.label().get());
      }
      return new Decision.Allow();
    } catch (RuntimeException e) {
      return new Decision.Fail("classifier invocation failed", e);
    }
  }
}
