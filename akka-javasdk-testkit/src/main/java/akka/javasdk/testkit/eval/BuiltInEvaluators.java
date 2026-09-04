/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.eval.Evaluator.EvalResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The evaluators a case's {@link Expectations} activate, one per declaration kind.
 *
 * <p>Each reads only the evidence it names and abstains when that evidence is absent, so one
 * missing tool call does not fail every check.
 */
final class BuiltInEvaluators {

  private BuiltInEvaluators() {}

  static List<Evaluator> activatedBy(Expectations expectations) {
    var evaluators = new ArrayList<Evaluator>();
    if (!expectations.expectedTools().isEmpty()) evaluators.add(tools(expectations));
    if (!expectations.expectedOrder().isEmpty()) evaluators.add(toolOrder(expectations));
    if (!expectations.toolArguments().isEmpty()) evaluators.add(toolArguments(expectations));
    if (!expectations.toolResults().isEmpty()) evaluators.add(toolResults(expectations));
    if (!expectations.forbidden().isEmpty()) evaluators.add(forbiddenTools(expectations));
    var budgets = expectations.budgets();
    budgets.toolCalls().ifPresent(limit -> evaluators.add(toolCallBudget(limit)));
    budgets.modelCalls().ifPresent(limit -> evaluators.add(modelCallBudget(limit)));
    budgets.tokens().ifPresent(limit -> evaluators.add(tokenBudget(limit)));
    budgets.latency().ifPresent(limit -> evaluators.add(latencyBudget(limit)));
    if (!expectations.answerNeedles().isEmpty()) evaluators.add(answerContains(expectations));
    expectations.answerRegex().ifPresent(regex -> evaluators.add(answerMatches(regex)));
    evaluators.addAll(expectations.evaluators());
    return List.copyOf(evaluators);
  }

  private static Evaluator tools(Expectations expectations) {
    return (evalCase, reply, calls) -> {
      var called = names(calls);
      var missing = expectations.expectedTools().stream().filter(t -> !called.contains(t)).toList();
      return missing.isEmpty()
          ? EvalResult.pass(Evaluators.TOOLS)
          : EvalResult.fail(
              Evaluators.TOOLS, "never called " + missing + "; called " + calledOrNothing(called));
    };
  }

  private static Evaluator toolOrder(Expectations expectations) {
    return (evalCase, reply, calls) -> {
      var expected = expectations.expectedOrder();
      var called = names(calls);
      var missing = expected.stream().filter(t -> !called.contains(t)).distinct().toList();
      if (!missing.isEmpty()) {
        return EvalResult.abstain(Evaluators.TOOL_ORDER, "never called " + missing);
      }
      return isSubsequence(expected, calls)
          ? EvalResult.pass(Evaluators.TOOL_ORDER)
          : EvalResult.fail(
              Evaluators.TOOL_ORDER,
              "expected " + expected + " in that order; called " + orderOf(calls));
    };
  }

  /** The expected names must appear in order. Other calls in between are allowed. */
  private static boolean isSubsequence(List<String> expected, List<ToolCall> calls) {
    int next = 0;
    for (var call : calls) {
      if (next < expected.size() && call.name().equals(expected.get(next))) next++;
    }
    return next == expected.size();
  }

  private static Evaluator toolArguments(Expectations expectations) {
    return (evalCase, reply, calls) -> {
      var failures = new ArrayList<String>();
      var abstentions = new ArrayList<String>();
      for (var declared : expectations.toolArguments()) {
        var toTheTool = calls.stream().filter(c -> c.name().equals(declared.tool())).toList();
        if (toTheTool.isEmpty()) {
          abstentions.add(declared.tool() + " was never called");
          continue;
        }
        var carried =
            toTheTool.stream()
                .anyMatch(c -> sameValue(declared.value(), c.arguments().get(declared.argument())));
        if (!carried) {
          failures.add(
              declared.tool()
                  + "("
                  + declared.argument()
                  + ") expected "
                  + declared.value()
                  + ", was "
                  + toTheTool.stream().map(c -> c.arguments().get(declared.argument())).toList());
        }
      }
      if (!failures.isEmpty()) {
        return EvalResult.fail(Evaluators.TOOL_ARGUMENTS, String.join("; ", failures));
      }
      if (abstentions.size() == expectations.toolArguments().size()) {
        return EvalResult.abstain(Evaluators.TOOL_ARGUMENTS, String.join("; ", abstentions));
      }
      return EvalResult.pass(Evaluators.TOOL_ARGUMENTS);
    };
  }

  /** A JSON number may deserialize to another numeric type, so compare rendered values as well. */
  private static boolean sameValue(Object expected, Object actual) {
    return Objects.equals(expected, actual)
        || (actual != null && String.valueOf(expected).equals(String.valueOf(actual)));
  }

  /** Abstains when the tool was never called or no result was recorded. */
  private static Evaluator toolResults(Expectations expectations) {
    return (evalCase, reply, calls) -> {
      var failures = new ArrayList<String>();
      var abstentions = new ArrayList<String>();
      for (var declared : expectations.toolResults()) {
        var results =
            calls.stream()
                .filter(c -> c.name().equals(declared.tool()))
                .flatMap(c -> c.result().stream())
                .toList();
        if (results.isEmpty()) {
          abstentions.add(declared.tool() + " has no recorded result");
          continue;
        }
        var needle = declared.needle().toLowerCase(Locale.ROOT);
        if (results.stream().noneMatch(r -> r.toLowerCase(Locale.ROOT).contains(needle))) {
          failures.add(
              declared.tool()
                  + " result expected to carry "
                  + declared.needle()
                  + ", was "
                  + results);
        }
      }
      if (!failures.isEmpty()) {
        return EvalResult.fail(Evaluators.TOOL_RESULTS, String.join("; ", failures));
      }
      if (abstentions.size() == expectations.toolResults().size()) {
        return EvalResult.abstain(Evaluators.TOOL_RESULTS, String.join("; ", abstentions));
      }
      return EvalResult.pass(Evaluators.TOOL_RESULTS);
    };
  }

  private static Evaluator forbiddenTools(Expectations expectations) {
    return (evalCase, reply, calls) -> {
      var called = names(calls);
      var forbidden = expectations.forbidden().stream().filter(called::contains).toList();
      return forbidden.isEmpty()
          ? EvalResult.pass(Evaluators.FORBIDDEN_TOOLS)
          : EvalResult.fail(Evaluators.FORBIDDEN_TOOLS, "called " + forbidden);
    };
  }

  private static Evaluator toolCallBudget(int limit) {
    return (evalCase, reply, calls) ->
        calls.size() <= limit
            ? EvalResult.pass(Evaluators.TOOL_CALL_BUDGET)
            : EvalResult.fail(
                Evaluators.TOOL_CALL_BUDGET,
                "made " + calls.size() + " tool calls, allowed " + limit + ": " + orderOf(calls));
  }

  /** Abstains when the trace carried no model calls. */
  private static Evaluator modelCallBudget(int limit) {
    return (evalCase, reply, calls) -> {
      var made = reply.modelCalls().size();
      if (made == 0) {
        return EvalResult.abstain(Evaluators.MODEL_CALL_BUDGET, "no model calls in the evidence");
      }
      return made <= limit
          ? EvalResult.pass(Evaluators.MODEL_CALL_BUDGET)
          : EvalResult.fail(
              Evaluators.MODEL_CALL_BUDGET, "made " + made + " model calls, allowed " + limit);
    };
  }

  /** Abstains when no model call reported tokens, as with a mocked model. */
  private static Evaluator tokenBudget(long limit) {
    return (evalCase, reply, calls) -> {
      var used = reply.totalTokens();
      if (used == 0) {
        return EvalResult.abstain(Evaluators.TOKEN_BUDGET, "no token counts in the evidence");
      }
      return used <= limit
          ? EvalResult.pass(Evaluators.TOKEN_BUDGET)
          : EvalResult.fail(
              Evaluators.TOKEN_BUDGET,
              "used "
                  + used
                  + " tokens ("
                  + reply.inputTokens()
                  + " in, "
                  + reply.outputTokens()
                  + " out), allowed "
                  + limit);
    };
  }

  /** Abstains when the evidence carries no timing. */
  private static Evaluator latencyBudget(Duration limit) {
    return (evalCase, reply, calls) -> {
      var took = reply.latency();
      if (took.isZero()) {
        return EvalResult.abstain(Evaluators.LATENCY_BUDGET, "no timing in the evidence");
      }
      return took.compareTo(limit) <= 0
          ? EvalResult.pass(Evaluators.LATENCY_BUDGET)
          : EvalResult.fail(
              Evaluators.LATENCY_BUDGET,
              "took " + took.toMillis() + " ms, allowed " + limit.toMillis() + " ms");
    };
  }

  private static Evaluator answerContains(Expectations expectations) {
    return (evalCase, reply, calls) -> {
      var text = reply.text().toLowerCase(Locale.ROOT);
      var missing =
          expectations.answerNeedles().stream()
              .filter(needle -> !text.contains(needle.toLowerCase(Locale.ROOT)))
              .toList();
      return missing.isEmpty()
          ? EvalResult.pass(Evaluators.ANSWER_CONTAINS)
          : EvalResult.fail(Evaluators.ANSWER_CONTAINS, "reply does not carry " + missing);
    };
  }

  private static Evaluator answerMatches(String regex) {
    Pattern pattern;
    try {
      pattern = Pattern.compile(regex, Pattern.DOTALL);
    } catch (PatternSyntaxException e) {
      return (evalCase, reply, calls) ->
          EvalResult.fail(Evaluators.ANSWER_MATCHES, "not a regular expression: " + regex);
    }
    return (evalCase, reply, calls) ->
        pattern.matcher(reply.text()).find()
            ? EvalResult.pass(Evaluators.ANSWER_MATCHES)
            : EvalResult.fail(Evaluators.ANSWER_MATCHES, "reply does not match /" + regex + "/");
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
