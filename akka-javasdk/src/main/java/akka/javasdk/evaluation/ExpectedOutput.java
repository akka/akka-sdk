/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import java.util.List;

/**
 * What a {@link DatasetItem} expects of the target's response, for a reference-requiring evaluator
 * to check.
 *
 * <p>An item expects one of these at a time — the response content, the tools that should have been
 * called, the arguments to one of those calls, or the order the calls should come in — and an
 * evaluator reads the variant it needs.
 */
public sealed interface ExpectedOutput {

  /** The final response should contain, match, or equal this value; the evaluator decides which. */
  record ResponseContent(String value) implements ExpectedOutput {}

  /** The tools the case expects to have been called. */
  record ExpectedTools(List<String> toolNames) implements ExpectedOutput {}

  /** The arguments, as a JSON string, expected for a call to the named tool. */
  record ToolArguments(String toolName, String argumentsJson) implements ExpectedOutput {}

  /** The order the named tools are expected to have been called in. */
  record ToolCallOrder(List<String> toolNames) implements ExpectedOutput {}
}
