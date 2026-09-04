/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

/**
 * One guardrail's verdict on a turn, as evidence: which guardrail, what it guards, and whether it
 * let the turn through.
 *
 * @param name the guardrail's configured name
 * @param category what it guards, as configured
 * @param passed whether it let the content through
 * @param explanation why it blocked, empty when it passed
 */
public record GuardrailResult(String name, String category, boolean passed, String explanation) {

  public GuardrailResult {
    name = name == null ? "" : name;
    category = category == null ? "" : category;
    explanation = explanation == null ? "" : explanation;
  }
}
