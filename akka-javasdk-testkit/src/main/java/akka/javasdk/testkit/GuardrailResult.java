/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

/**
 * One guardrail evaluation during a turn.
 *
 * @param name the configured name of the guardrail
 * @param category the configured category
 * @param decision what the guardrail decided; null becomes {@link Decision#ALLOW}
 * @param explanation why it denied or failed, empty when it allowed
 */
public record GuardrailResult(String name, String category, Decision decision, String explanation) {

  /** What a guardrail decided about the content it checked. */
  public enum Decision {
    /** The guardrail let the content through. */
    ALLOW,
    /** The guardrail denied the content. */
    DENY,
    /** The guardrail failed to evaluate the content. */
    ERROR
  }

  public GuardrailResult {
    name = name == null ? "" : name;
    category = category == null ? "" : category;
    decision = decision == null ? Decision.ALLOW : decision;
    explanation = explanation == null ? "" : explanation;
  }

  /** A result that is {@link Decision#ALLOW} when passed and {@link Decision#ERROR} otherwise. */
  public GuardrailResult(String name, String category, boolean passed, String explanation) {
    this(name, category, passed ? Decision.ALLOW : Decision.ERROR, explanation);
  }

  /** Whether the guardrail let the content through. */
  public boolean passed() {
    return decision == Decision.ALLOW;
  }
}
