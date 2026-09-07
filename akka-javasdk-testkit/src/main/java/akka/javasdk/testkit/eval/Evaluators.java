/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.ToolCall;
import akka.javasdk.testkit.eval.Evaluator.EvalResult;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The built-in evaluators, and the names every evaluator reports under.
 *
 * <p>Each factory returns an {@link Evaluator} for one check over the reply and the traced tool
 * calls. An evaluator reads only the evidence it names and abstains when that evidence is absent: a
 * tool that was never called fails {@link #tools}, and {@link #toolArgument} abstains. Give the
 * evaluators to an {@link EvalCase}, next to a {@link Judge} or a custom evaluator.
 *
 * <p>The name constants are what the report prints and what {@link Gate#evaluatorRateAtLeast}
 * refers to. {@link #TARGET} and {@link #SETUP} are reported by the runner when a case did not
 * reach evaluation.
 */
public final class Evaluators {

  /** {@link #tools}: every named tool was called. */
  public static final String TOOLS = "tools";

  /** {@link #toolOrder}: the named tools were called in that relative order. */
  public static final String TOOL_ORDER = "tool-order";

  /** {@link #toolArgument}: the tool was called with the argument value. */
  public static final String TOOL_ARGUMENTS = "tool-arguments";

  /** {@link #toolResult}: the tool's result carried the text. */
  public static final String TOOL_RESULTS = "tool-results";

  /** {@link #toolCallsAtMost}: the agent made at most that many tool calls. */
  public static final String TOOL_CALL_BUDGET = "tool-call-budget";

  /** {@link #modelCallsAtMost}: the agent made at most that many model calls. */
  public static final String MODEL_CALL_BUDGET = "model-call-budget";

  /** {@link #tokensAtMost}: the turn used at most that many tokens, in and out. */
  public static final String TOKEN_BUDGET = "token-budget";

  /** {@link #latencyAtMost}: the turn was answered within that time. */
  public static final String LATENCY_BUDGET = "latency-budget";

  /** {@link #forbiddenTools}: none of the named tools was called. */
  public static final String FORBIDDEN_TOOLS = "forbidden-tools";

  /** {@link #answerContains}: the reply carries every needle. */
  public static final String ANSWER_CONTAINS = "answer-contains";

  /** {@link #answerMatches}: the reply matches the pattern. */
  public static final String ANSWER_MATCHES = "answer-matches";

  /** {@link Judge}: a model scored the reply against a criterion. */
  public static final String JUDGE = "judge";

  /** The agent call failed. */
  public static final String TARGET = "target";

  /** The case's setup threw, so the agent was never called. */
  public static final String SETUP = "setup";

  private Evaluators() {}

  /** These tools must be called, in any order. Other calls are allowed. */
  public static Evaluator tools(String... names) {
    var expected = toolNames(names);
    return (evalCase, reply, calls) -> {
      var called = names(calls);
      var missing = expected.stream().filter(t -> !called.contains(t)).toList();
      return missing.isEmpty()
          ? EvalResult.pass(TOOLS)
          : EvalResult.fail(
              TOOLS, "never called " + missing + "; called " + calledOrNothing(called));
    };
  }

  /**
   * These tools must be called in this relative order. Calls to other tools may come between.
   * Abstains when one of them was never called.
   */
  public static Evaluator toolOrder(String... names) {
    var expected = List.copyOf(toolNames(names));
    return (evalCase, reply, calls) -> {
      var called = names(calls);
      var missing = expected.stream().filter(t -> !called.contains(t)).toList();
      if (!missing.isEmpty()) {
        return EvalResult.abstain(TOOL_ORDER, "never called " + missing);
      }
      return isSubsequence(expected, calls)
          ? EvalResult.pass(TOOL_ORDER)
          : EvalResult.fail(
              TOOL_ORDER, "expected " + expected + " in that order; called " + orderOf(calls));
    };
  }

  /**
   * The named tool must be called with this argument value. Numbers compare by value. Abstains when
   * the tool was never called.
   */
  public static Evaluator toolArgument(String tool, String argument, Object value) {
    requireName(tool, "tool");
    requireName(argument, "argument");
    return (evalCase, reply, calls) -> {
      var toTheTool = calls.stream().filter(c -> c.name().equals(tool)).toList();
      if (toTheTool.isEmpty()) {
        return EvalResult.abstain(TOOL_ARGUMENTS, tool + " was never called");
      }
      var carried = toTheTool.stream().anyMatch(c -> sameValue(value, c.arguments().get(argument)));
      return carried
          ? EvalResult.pass(TOOL_ARGUMENTS)
          : EvalResult.fail(
              TOOL_ARGUMENTS,
              tool
                  + "("
                  + argument
                  + ") expected "
                  + value
                  + ", was "
                  + toTheTool.stream().map(c -> c.arguments().get(argument)).toList());
    };
  }

  /**
   * The result of the named tool must contain this text, case-insensitively. Abstains when the tool
   * was never called or the trace carries no result for it.
   */
  public static Evaluator toolResult(String tool, String needle) {
    requireName(tool, "tool");
    if (needle == null || needle.isEmpty()) throw new IllegalArgumentException("needle required");
    var lowerNeedle = needle.toLowerCase(Locale.ROOT);
    return (evalCase, reply, calls) -> {
      var results =
          calls.stream()
              .filter(c -> c.name().equals(tool))
              .flatMap(c -> c.result().stream())
              .toList();
      if (results.isEmpty()) {
        return EvalResult.abstain(TOOL_RESULTS, tool + " has no recorded result");
      }
      return results.stream().anyMatch(r -> r.toLowerCase(Locale.ROOT).contains(lowerNeedle))
          ? EvalResult.pass(TOOL_RESULTS)
          : EvalResult.fail(
              TOOL_RESULTS, tool + " result expected to carry " + needle + ", was " + results);
    };
  }

  /** None of these tools may be called. */
  public static Evaluator forbiddenTools(String... names) {
    var forbidden = toolNames(names);
    return (evalCase, reply, calls) -> {
      var called = names(calls);
      var violated = forbidden.stream().filter(called::contains).toList();
      return violated.isEmpty()
          ? EvalResult.pass(FORBIDDEN_TOOLS)
          : EvalResult.fail(FORBIDDEN_TOOLS, "called " + violated);
    };
  }

  /** The agent may make at most this many tool calls while answering. */
  public static Evaluator toolCallsAtMost(int calls) {
    if (calls < 0) throw new IllegalArgumentException("a budget is not negative");
    return (evalCase, reply, made) ->
        made.size() <= calls
            ? EvalResult.pass(TOOL_CALL_BUDGET)
            : EvalResult.fail(
                TOOL_CALL_BUDGET,
                "made " + made.size() + " tool calls, allowed " + calls + ": " + orderOf(made));
  }

  /**
   * The agent may make at most this many model calls while answering. Abstains when the trace
   * carried no model calls.
   */
  public static Evaluator modelCallsAtMost(int calls) {
    if (calls < 1) throw new IllegalArgumentException("an answer takes at least one model call");
    return (evalCase, reply, toolCalls) -> {
      var made = reply.modelCalls().size();
      if (made == 0) {
        return EvalResult.abstain(MODEL_CALL_BUDGET, "no model calls in the evidence");
      }
      return made <= calls
          ? EvalResult.pass(MODEL_CALL_BUDGET)
          : EvalResult.fail(MODEL_CALL_BUDGET, "made " + made + " model calls, allowed " + calls);
    };
  }

  /**
   * The turn may use at most this many tokens, input and output together. Abstains when no model
   * call reported tokens, as with a mocked model.
   */
  public static Evaluator tokensAtMost(long tokens) {
    if (tokens < 1) throw new IllegalArgumentException("a token budget is positive");
    return (evalCase, reply, calls) -> {
      var used = reply.totalTokens();
      if (used == 0) {
        return EvalResult.abstain(TOKEN_BUDGET, "no token counts in the evidence");
      }
      return used <= tokens
          ? EvalResult.pass(TOKEN_BUDGET)
          : EvalResult.fail(
              TOKEN_BUDGET,
              "used "
                  + used
                  + " tokens ("
                  + reply.inputTokens()
                  + " in, "
                  + reply.outputTokens()
                  + " out), allowed "
                  + tokens);
    };
  }

  /**
   * The agent command must complete within this time. Abstains when the trace carries no timing.
   */
  public static Evaluator latencyAtMost(Duration latency) {
    if (latency == null || latency.isNegative() || latency.isZero())
      throw new IllegalArgumentException("a latency budget is positive");
    return (evalCase, reply, calls) -> {
      var took = reply.latency();
      if (took.isZero()) {
        return EvalResult.abstain(LATENCY_BUDGET, "no timing in the evidence");
      }
      return took.compareTo(latency) <= 0
          ? EvalResult.pass(LATENCY_BUDGET)
          : EvalResult.fail(
              LATENCY_BUDGET,
              "took " + took.toMillis() + " ms, allowed " + latency.toMillis() + " ms");
    };
  }

  /** The reply must contain every needle, case-insensitively. */
  public static Evaluator answerContains(String... needles) {
    if (needles == null || needles.length == 0)
      throw new IllegalArgumentException("at least one needle required");
    var expected = List.of(needles);
    return (evalCase, reply, calls) -> {
      var text = reply.text().toLowerCase(Locale.ROOT);
      var missing =
          expected.stream()
              .filter(needle -> !text.contains(needle.toLowerCase(Locale.ROOT)))
              .toList();
      return missing.isEmpty()
          ? EvalResult.pass(ANSWER_CONTAINS)
          : EvalResult.fail(ANSWER_CONTAINS, "reply does not carry " + missing);
    };
  }

  /** The reply must match the regular expression, anywhere in it. Anchor it for a full match. */
  public static Evaluator answerMatches(String regex) {
    if (regex == null) throw new IllegalArgumentException("regex required");
    Pattern pattern;
    try {
      pattern = Pattern.compile(regex, Pattern.DOTALL);
    } catch (PatternSyntaxException e) {
      return (evalCase, reply, calls) ->
          EvalResult.fail(ANSWER_MATCHES, "not a regular expression: " + regex);
    }
    return (evalCase, reply, calls) ->
        pattern.matcher(reply.text()).find()
            ? EvalResult.pass(ANSWER_MATCHES)
            : EvalResult.fail(ANSWER_MATCHES, "reply does not match /" + regex + "/");
  }

  private static Set<String> toolNames(String... names) {
    if (names == null || names.length == 0)
      throw new IllegalArgumentException("at least one tool name required");
    var set = new LinkedHashSet<String>();
    for (var name : names) {
      requireName(name, "tool");
      set.add(name);
    }
    return set;
  }

  private static void requireName(String name, String what) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException(what + " name required");
  }

  /** The expected names must appear in order. Other calls in between are allowed. */
  private static boolean isSubsequence(List<String> expected, List<ToolCall> calls) {
    int next = 0;
    for (var call : calls) {
      if (next < expected.size() && call.name().equals(expected.get(next))) next++;
    }
    return next == expected.size();
  }

  /** A JSON number may deserialize to another numeric type, so compare rendered values as well. */
  private static boolean sameValue(Object expected, Object actual) {
    return Objects.equals(expected, actual)
        || (actual != null && String.valueOf(expected).equals(String.valueOf(actual)));
  }

  private static LinkedHashSet<String> names(List<ToolCall> calls) {
    return calls.stream()
        .map(ToolCall::name)
        .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
  }

  private static List<String> orderOf(List<ToolCall> calls) {
    return calls.stream().map(ToolCall::name).toList();
  }

  private static Object calledOrNothing(LinkedHashSet<String> called) {
    return called.isEmpty() ? "no tools" : called;
  }
}
