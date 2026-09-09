/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

/**
 * One guardrail evaluation during a turn.
 *
 * @param name the configured name of the guardrail
 * @param category the configured category
 * @param passed whether the guardrail let the content through
 * @param explanation why it blocked, empty when it passed
 */
public record GuardrailResult(String name, String category, boolean passed, String explanation) {

  public GuardrailResult {
    name = name == null ? "" : name;
    category = category == null ? "" : category;
    explanation = explanation == null ? "" : explanation;
  }
}
