/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/**
 * What a case asserts, as data.
 *
 * <p>Each declared expectation activates one built-in evaluator over the case's evidence (the reply
 * text and the drained tool calls). An expectation reads only what it names and abstains on what it
 * cannot reach — "the tool was never called" fails {@link #tools}, not {@link #toolArgument}.
 *
 * <p>This class only carries the declarations; {@link ExperimentRunner} reads them through the
 * accessors. Immutable: every method returns a new instance.
 */
public final class Expectations {

  /** One declared argument expectation: the named call must carry this value. */
  public record ToolArgument(String tool, String argument, Object value) {}

  /** One declared result expectation: what the named call returned must contain this text. */
  public record ToolResult(String tool, String needle) {}

  /**
   * The ceilings a turn is held to. Each is read against the evidence the trace carried and
   * abstains when the trace did not carry that figure.
   */
  public record Budgets(
      OptionalInt toolCalls,
      OptionalInt modelCalls,
      OptionalLong tokens,
      Optional<Duration> latency) {

    public static final Budgets NONE =
        new Budgets(
            OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty(), Optional.empty());

    public boolean any() {
      return toolCalls.isPresent()
          || modelCalls.isPresent()
          || tokens.isPresent()
          || latency.isPresent();
    }
  }

  private final Set<String> expectedTools;
  private final List<String> expectedOrder;
  private final List<ToolArgument> toolArguments;
  private final List<ToolResult> toolResults;
  private final Set<String> forbiddenTools;
  private final List<String> answerNeedles;
  private final Optional<String> answerRegex;
  private final List<Evaluator> evaluators;
  private final Budgets budgets;

  private Expectations(
      Set<String> expectedTools,
      List<String> expectedOrder,
      List<ToolArgument> toolArguments,
      List<ToolResult> toolResults,
      Set<String> forbiddenTools,
      List<String> answerNeedles,
      Optional<String> answerRegex,
      List<Evaluator> evaluators,
      Budgets budgets) {
    this.expectedTools = Set.copyOf(expectedTools);
    this.expectedOrder = List.copyOf(expectedOrder);
    this.toolArguments = List.copyOf(toolArguments);
    this.toolResults = List.copyOf(toolResults);
    this.forbiddenTools = Set.copyOf(forbiddenTools);
    this.answerNeedles = List.copyOf(answerNeedles);
    this.answerRegex = answerRegex;
    this.evaluators = List.copyOf(evaluators);
    this.budgets = budgets == null ? Budgets.NONE : budgets;
  }

  private static final Expectations EMPTY =
      new Expectations(
          Set.of(),
          List.of(),
          List.of(),
          List.of(),
          Set.of(),
          List.of(),
          Optional.empty(),
          List.of(),
          Budgets.NONE);

  /** The entry point: no expectations yet. */
  public static Expectations expect() {
    return EMPTY;
  }

  /** A case with nothing to assert, useful only as a placeholder while authoring. */
  public static Expectations none() {
    return EMPTY;
  }

  /** These tools must be called, in any order. Other calls are allowed. */
  public Expectations tools(String... names) {
    var next = new LinkedHashSet<>(expectedTools);
    next.addAll(List.of(names));
    return new Expectations(
        next,
        expectedOrder,
        toolArguments,
        toolResults,
        forbiddenTools,
        answerNeedles,
        answerRegex,
        evaluators,
        budgets);
  }

  /** These tools must be called in exactly this relative order. */
  public Expectations toolOrder(String... names) {
    return new Expectations(
        expectedTools,
        List.of(names),
        toolArguments,
        toolResults,
        forbiddenTools,
        answerNeedles,
        answerRegex,
        evaluators,
        budgets);
  }

  /** The named call must carry this argument with this value. */
  public Expectations toolArgument(String tool, String argument, Object value) {
    var next = new ArrayList<>(toolArguments);
    next.add(new ToolArgument(tool, argument, value));
    return new Expectations(
        expectedTools,
        expectedOrder,
        next,
        toolResults,
        forbiddenTools,
        answerNeedles,
        answerRegex,
        evaluators,
        budgets);
  }

  /**
   * What the named call returned must contain this text, case-insensitively. Abstains over evidence
   * that carries no results.
   */
  public Expectations toolResult(String tool, String needle) {
    var next = new ArrayList<>(toolResults);
    next.add(new ToolResult(tool, needle));
    return new Expectations(
        expectedTools,
        expectedOrder,
        toolArguments,
        next,
        forbiddenTools,
        answerNeedles,
        answerRegex,
        evaluators,
        budgets);
  }

  /** The agent may make at most this many tool calls while answering. */
  public Expectations toolCallsAtMost(int calls) {
    if (calls < 0) throw new IllegalArgumentException("a budget is not negative");
    return withBudgets(
        new Budgets(
            OptionalInt.of(calls), budgets.modelCalls(), budgets.tokens(), budgets.latency()));
  }

  /** The agent may make at most this many model calls while answering. */
  public Expectations modelCallsAtMost(int calls) {
    if (calls < 1) throw new IllegalArgumentException("an answer takes at least one model call");
    return withBudgets(
        new Budgets(
            budgets.toolCalls(), OptionalInt.of(calls), budgets.tokens(), budgets.latency()));
  }

  /** The turn may use at most this many tokens, input and output together. */
  public Expectations tokensAtMost(long tokens) {
    if (tokens < 1) throw new IllegalArgumentException("a token budget is positive");
    return withBudgets(
        new Budgets(
            budgets.toolCalls(), budgets.modelCalls(), OptionalLong.of(tokens), budgets.latency()));
  }

  /** The turn must be answered within this time, measured on the agent's command. */
  public Expectations latencyAtMost(Duration latency) {
    if (latency == null || latency.isNegative() || latency.isZero())
      throw new IllegalArgumentException("a latency budget is positive");
    return withBudgets(
        new Budgets(
            budgets.toolCalls(), budgets.modelCalls(), budgets.tokens(), Optional.of(latency)));
  }

  private Expectations withBudgets(Budgets next) {
    return new Expectations(
        expectedTools,
        expectedOrder,
        toolArguments,
        toolResults,
        forbiddenTools,
        answerNeedles,
        answerRegex,
        evaluators,
        next);
  }

  /** None of these tools may be called. */
  public Expectations forbiddenTools(String... names) {
    var next = new LinkedHashSet<>(forbiddenTools);
    next.addAll(List.of(names));
    return new Expectations(
        expectedTools,
        expectedOrder,
        toolArguments,
        toolResults,
        next,
        answerNeedles,
        answerRegex,
        evaluators,
        budgets);
  }

  /** The reply must contain every needle, case-insensitively. */
  public Expectations answerContains(String... needles) {
    var next = new ArrayList<>(answerNeedles);
    next.addAll(List.of(needles));
    return new Expectations(
        expectedTools,
        expectedOrder,
        toolArguments,
        toolResults,
        forbiddenTools,
        next,
        answerRegex,
        evaluators,
        budgets);
  }

  /** The reply must match the regular expression, anywhere in it. Anchor it for a full match. */
  public Expectations answerMatches(String regex) {
    return new Expectations(
        expectedTools,
        expectedOrder,
        toolArguments,
        toolResults,
        forbiddenTools,
        answerNeedles,
        Optional.of(regex),
        evaluators,
        budgets);
  }

  /**
   * Anything the built-ins cannot express, as code over the same evidence.
   *
   * <p>This is also where a model judge lands: {@link Judge#mustSatisfy} produces one of these from
   * a criterion written in words. The seam holds no model, so the provider and the bill stay the
   * consumer's.
   */
  public Expectations satisfies(Evaluator evaluator) {
    var next = new ArrayList<>(evaluators);
    next.add(evaluator);
    return new Expectations(
        expectedTools,
        expectedOrder,
        toolArguments,
        toolResults,
        forbiddenTools,
        answerNeedles,
        answerRegex,
        next,
        budgets);
  }

  // What the runner reads.

  public Set<String> expectedTools() {
    return expectedTools;
  }

  public List<String> expectedOrder() {
    return expectedOrder;
  }

  public List<ToolArgument> toolArguments() {
    return toolArguments;
  }

  public List<ToolResult> toolResults() {
    return toolResults;
  }

  public Set<String> forbidden() {
    return forbiddenTools;
  }

  public List<String> answerNeedles() {
    return answerNeedles;
  }

  public Optional<String> answerRegex() {
    return answerRegex;
  }

  public List<Evaluator> evaluators() {
    return evaluators;
  }

  public Budgets budgets() {
    return budgets;
  }
}
