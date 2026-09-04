/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

/**
 * The names the built-in evaluators report under. {@link Gate#evaluatorRateAtLeast} names one, and
 * the report prints them.
 *
 * <p>Each is activated by the matching method on {@link Expectations}, except {@link #TARGET} and
 * {@link #SETUP}, which the runner reports when a case did not reach evaluation.
 */
public final class Evaluators {

  /** {@link Expectations#tools}: every named tool was called. */
  public static final String TOOLS = "tools";

  /** {@link Expectations#toolOrder}: the named tools were called in that relative order. */
  public static final String TOOL_ORDER = "tool-order";

  /** {@link Expectations#toolArgument}: every declared argument arrived with its value. */
  public static final String TOOL_ARGUMENTS = "tool-arguments";

  /** {@link Expectations#toolResult}: the named tool's result carried the text. */
  public static final String TOOL_RESULTS = "tool-results";

  /** {@link Expectations#toolCallsAtMost}: the agent made at most that many tool calls. */
  public static final String TOOL_CALL_BUDGET = "tool-call-budget";

  /** {@link Expectations#modelCallsAtMost}: the agent made at most that many model calls. */
  public static final String MODEL_CALL_BUDGET = "model-call-budget";

  /** {@link Expectations#tokensAtMost}: the turn used at most that many tokens, in and out. */
  public static final String TOKEN_BUDGET = "token-budget";

  /** {@link Expectations#latencyAtMost}: the turn was answered within that time. */
  public static final String LATENCY_BUDGET = "latency-budget";

  /** {@link Expectations#forbiddenTools}: none of the named tools was called. */
  public static final String FORBIDDEN_TOOLS = "forbidden-tools";

  /** {@link Expectations#answerContains}: the reply carries every needle. */
  public static final String ANSWER_CONTAINS = "answer-contains";

  /** {@link Expectations#answerMatches}: the reply matches the pattern. */
  public static final String ANSWER_MATCHES = "answer-matches";

  /** {@link Judge}: a model scored the reply against a criterion. */
  public static final String JUDGE = "judge";

  /** The agent call failed. */
  public static final String TARGET = "target";

  /** The case's setup threw, so the agent was never called. */
  public static final String SETUP = "setup";

  private Evaluators() {}
}
