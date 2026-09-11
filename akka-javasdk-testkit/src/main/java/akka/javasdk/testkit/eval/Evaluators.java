/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.ToolCall;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
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
 * calls. An evaluator reads only the evidence it names and is inconclusive when that evidence is
 * absent: a tool that was never called fails {@link #tools}, and {@link #toolArgument} is
 * inconclusive. Give the evaluators to an {@link EvalCase}, next to a {@link Judge} or a custom
 * evaluator.
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

  /** {@link #answerContains}: the reply carries every given text. */
  public static final String ANSWER_CONTAINS = "answer-contains";

  /** {@link #answerMatches}: the reply matches the pattern. */
  public static final String ANSWER_MATCHES = "answer-matches";

  /** {@link #answerLacks}: the reply carries none of the given texts. */
  public static final String ANSWER_LACKS = "answer-lacks";

  /** {@link #answerDoesNotMatch}: the reply does not match the pattern. */
  public static final String ANSWER_DOES_NOT_MATCH = "answer-does-not-match";

  /** {@link #answerLacksLuhnNumber}: the reply carries no number passing the Luhn checksum. */
  public static final String ANSWER_LACKS_LUHN_NUMBER = "answer-lacks-luhn-number";

  /** {@link #answerLacksPaymentCard}: the reply carries no payment card number. */
  public static final String ANSWER_LACKS_PAYMENT_CARD = "answer-lacks-payment-card";

  /** {@link Judge}: a model scored the reply against a criterion. */
  public static final String JUDGE = "judge";

  /** The agent call failed. */
  public static final String TARGET = "target";

  /** Loading the case's recorded calls into the stubs threw, so the agent was never called. */
  public static final String SETUP = "setup";

  private Evaluators() {}

  /** These tools must be called, in any order. Other calls are allowed. */
  public static Evaluator tools(String... names) {
    return new Tools(toolNames(names));
  }

  /**
   * These tools must be called in this relative order. Calls to other tools may come between.
   * Inconclusive when one of them was never called.
   */
  public static Evaluator toolOrder(String... names) {
    return new ToolOrder(List.copyOf(toolNames(names)));
  }

  /**
   * The named tool must be called with this argument value. Numbers compare by value. Inconclusive
   * when the tool was never called.
   */
  public static Evaluator toolArgument(String tool, String argument, Object value) {
    requireName(tool, "tool");
    requireName(argument, "argument");
    return new ToolArgument(tool, argument, value);
  }

  /**
   * The result of the named tool must contain this text, case-insensitively. Inconclusive when the
   * tool was never called or the trace carries no result for it.
   */
  public static Evaluator toolResult(String tool, String text) {
    requireName(tool, "tool");
    if (text == null || text.isEmpty()) throw new IllegalArgumentException("text required");
    return new ToolResult(tool, text);
  }

  /** None of these tools may be called. */
  public static Evaluator forbiddenTools(String... names) {
    return new ForbiddenTools(toolNames(names));
  }

  /** The agent may make at most this many tool calls while answering. */
  public static Evaluator toolCallsAtMost(int calls) {
    if (calls < 0) throw new IllegalArgumentException("a budget is not negative");
    return new ToolCallBudget(calls);
  }

  /**
   * The agent may make at most this many model calls while answering. Inconclusive when the trace
   * carried no model calls.
   */
  public static Evaluator modelCallsAtMost(int calls) {
    if (calls < 1) throw new IllegalArgumentException("an answer takes at least one model call");
    return new ModelCallBudget(calls);
  }

  /**
   * The turn may use at most this many tokens, input and output together. Inconclusive when no
   * model call reported tokens, as with a mocked model.
   */
  public static Evaluator tokensAtMost(long tokens) {
    if (tokens < 1) throw new IllegalArgumentException("a token budget is positive");
    return new TokenBudget(tokens);
  }

  /**
   * The agent command must complete within this time. Inconclusive when the trace carries no
   * timing.
   */
  public static Evaluator latencyAtMost(Duration latency) {
    if (latency == null || latency.isNegative() || latency.isZero())
      throw new IllegalArgumentException("a latency budget is positive");
    return new LatencyBudget(latency);
  }

  /** The reply must contain every given text, case-insensitively. */
  public static Evaluator answerContains(String... texts) {
    if (texts == null || texts.length == 0)
      throw new IllegalArgumentException("at least one text required");
    return new AnswerContains(List.of(texts));
  }

  /** The reply must match the regular expression, anywhere in it. Anchor it for a full match. */
  public static Evaluator answerMatches(String regex) {
    if (regex == null) throw new IllegalArgumentException("regex required");
    return new AnswerMatches(regex);
  }

  /** The reply must not contain any of the given texts, case-insensitively. */
  public static Evaluator answerLacks(String... texts) {
    if (texts == null || texts.length == 0)
      throw new IllegalArgumentException("at least one text required");
    for (var text : texts) {
      if (text == null || text.isBlank()) throw new IllegalArgumentException("text required");
    }
    return new AnswerLacks(List.of(texts));
  }

  /** The reply must not match the regular expression anywhere in it. */
  public static Evaluator answerDoesNotMatch(String regex) {
    if (regex == null) throw new IllegalArgumentException("regex required");
    return new AnswerDoesNotMatch(regex);
  }

  /**
   * The reply must not contain a sequence of digits within this length range that passes the Luhn checksum.
   * Spaces and dashes are allowed between the digits.
   *
   * <p>The Luhn checksum validates many identifiers besides payment cards, and each has its own
   * length: an IMEI has 15 digits, a South African ID 13, a Canadian Social Insurance Number 9.
   */
  public static Evaluator answerLacksLuhnNumber(int minDigits, int maxDigits) {
    if (minDigits < 2) throw new IllegalArgumentException("a Luhn number has at least two digits");
    if (maxDigits < minDigits)
      throw new IllegalArgumentException("maxDigits is below minDigits: " + maxDigits);
    return new AnswerLacksLuhnNumber(luhnCandidate(minDigits, maxDigits), ANSWER_LACKS_LUHN_NUMBER);
  }

  /**
   * The reply must not contain a payment card number. A card number is a run of 13 to 19 digits, spaces
   * and dashes allowed between them, that passes the Luhn checksum.
   */
  public static Evaluator answerLacksPaymentCard() {
    return new AnswerLacksLuhnNumber(luhnCandidate(13, 19), ANSWER_LACKS_PAYMENT_CARD);
  }

  private record Tools(Set<String> expected) implements Evaluator {
    @Override
    public String name() {
      return TOOLS;
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var calls = interaction.toolCalls();
      var called = names(calls);
      var missing = expected.stream().filter(t -> !called.contains(t)).toList();
      return missing.isEmpty()
          ? EvalResult.pass()
          : EvalResult.fail("never called " + missing + "; called " + calledOrNothing(called));
    }
  }

  private record ToolOrder(List<String> expected) implements Evaluator {
    @Override
    public String name() {
      return TOOL_ORDER;
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var calls = interaction.toolCalls();
      var called = names(calls);
      var missing = expected.stream().filter(t -> !called.contains(t)).toList();
      if (!missing.isEmpty()) {
        return EvalResult.inconclusive("never called " + missing);
      }
      return isSubsequence(expected, calls)
          ? EvalResult.pass()
          : EvalResult.fail("expected " + expected + " in that order; called " + orderOf(calls));
    }

    /** The expected names must appear in order. Other calls in between are allowed. */
    private static boolean isSubsequence(List<String> expected, List<ToolCall> calls) {
      int next = 0;
      for (var call : calls) {
        if (next < expected.size() && call.name().equals(expected.get(next))) next++;
      }
      return next == expected.size();
    }
  }

  private record ToolArgument(String tool, String argument, Object value) implements Evaluator {
    @Override
    public String name() {
      return TOOL_ARGUMENTS;
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var calls = interaction.toolCalls();
      var toTheTool = calls.stream().filter(c -> c.name().equals(tool)).toList();
      if (toTheTool.isEmpty()) {
        return EvalResult.inconclusive(tool + " was never called");
      }
      var carried = toTheTool.stream().anyMatch(c -> sameValue(value, c.arguments().get(argument)));
      return carried
          ? EvalResult.pass()
          : EvalResult.fail(
              tool
                  + "("
                  + argument
                  + ") expected "
                  + value
                  + ", was "
                  + toTheTool.stream().map(c -> c.arguments().get(argument)).toList());
    }

    // A JSON number may deserialize to another numeric type, so numbers compare by value. Any
    // other type must match exactly: a string where a number was recorded is a regression.
    private static boolean sameValue(Object expected, Object actual) {
      if (Objects.equals(expected, actual)) return true;
      if (expected instanceof Number a && actual instanceof Number b) {
        try {
          return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        } catch (NumberFormatException e) {
          return false; // NaN or infinity
        }
      }
      return false;
    }
  }

  private record ToolResult(String tool, String text) implements Evaluator {
    @Override
    public String name() {
      return TOOL_RESULTS;
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var calls = interaction.toolCalls();
      var results =
          calls.stream()
              .filter(c -> c.name().equals(tool))
              .flatMap(c -> c.result().stream())
              .toList();
      if (results.isEmpty()) {
        return EvalResult.inconclusive(tool + " has no recorded result");
      }
      var lowerText = text.toLowerCase(Locale.ROOT);
      return results.stream().anyMatch(r -> r.toLowerCase(Locale.ROOT).contains(lowerText))
          ? EvalResult.pass()
          : EvalResult.fail(tool + " result expected to carry " + text + ", was " + results);
    }
  }

  private record ForbiddenTools(Set<String> forbidden) implements Evaluator {
    @Override
    public String name() {
      return FORBIDDEN_TOOLS;
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var calls = interaction.toolCalls();
      var called = names(calls);
      var violated = forbidden.stream().filter(called::contains).toList();
      return violated.isEmpty() ? EvalResult.pass() : EvalResult.fail("called " + violated);
    }
  }

  private record ToolCallBudget(int limit) implements Evaluator {
    @Override
    public String name() {
      return TOOL_CALL_BUDGET;
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var calls = interaction.toolCalls();
      return calls.size() <= limit
          ? EvalResult.pass()
          : EvalResult.fail(
              "made " + calls.size() + " tool calls, allowed " + limit + ": " + orderOf(calls));
    }
  }

  private record ModelCallBudget(int limit) implements Evaluator {
    @Override
    public String name() {
      return MODEL_CALL_BUDGET;
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var made = interaction.modelCalls().size();
      if (made == 0) {
        return EvalResult.inconclusive("no model calls in the evidence");
      }
      return made <= limit
          ? EvalResult.pass()
          : EvalResult.fail("made " + made + " model calls, allowed " + limit);
    }
  }

  private record TokenBudget(long limit) implements Evaluator {
    @Override
    public String name() {
      return TOKEN_BUDGET;
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var used = interaction.totalTokens();
      if (used == 0) {
        return EvalResult.inconclusive("no token counts in the evidence");
      }
      return used <= limit
          ? EvalResult.pass()
          : EvalResult.fail(
              "used "
                  + used
                  + " tokens ("
                  + interaction.inputTokens()
                  + " in, "
                  + interaction.outputTokens()
                  + " out), allowed "
                  + limit);
    }
  }

  private record LatencyBudget(Duration limit) implements Evaluator {
    @Override
    public String name() {
      return LATENCY_BUDGET;
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var took = interaction.latency();
      if (took.isZero()) {
        return EvalResult.inconclusive("no timing in the evidence");
      }
      return took.compareTo(limit) <= 0
          ? EvalResult.pass()
          : EvalResult.fail("took " + took.toMillis() + " ms, allowed " + limit.toMillis() + " ms");
    }
  }

  private record AnswerContains(List<String> texts) implements Evaluator {
    @Override
    public String name() {
      return ANSWER_CONTAINS;
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var text = interaction.reply().toLowerCase(Locale.ROOT);
      var missing =
          texts.stream()
              .filter(expected -> !text.contains(expected.toLowerCase(Locale.ROOT)))
              .toList();
      return missing.isEmpty()
          ? EvalResult.pass()
          : EvalResult.fail("reply does not carry " + missing);
    }
  }

  private record AnswerMatches(String regex) implements Evaluator {
    @Override
    public String name() {
      return ANSWER_MATCHES;
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      Pattern pattern;
      try {
        pattern = Pattern.compile(regex, Pattern.DOTALL);
      } catch (PatternSyntaxException e) {
        return EvalResult.fail("not a regular expression: " + regex);
      }
      return pattern.matcher(interaction.reply()).find()
          ? EvalResult.pass()
          : EvalResult.fail("reply does not match /" + regex + "/");
    }
  }

  private record AnswerLacks(List<String> texts) implements Evaluator {
    @Override
    public String name() {
      return ANSWER_LACKS;
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var text = interaction.reply().toLowerCase(Locale.ROOT);
      var found =
          texts.stream()
              .filter(forbidden -> text.contains(forbidden.toLowerCase(Locale.ROOT)))
              .toList();
      return found.isEmpty() ? EvalResult.pass() : EvalResult.fail("reply carries " + found);
    }
  }

  private record AnswerDoesNotMatch(String regex) implements Evaluator {
    @Override
    public String name() {
      return ANSWER_DOES_NOT_MATCH;
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      Pattern pattern;
      try {
        pattern = Pattern.compile(regex, Pattern.DOTALL);
      } catch (PatternSyntaxException e) {
        return EvalResult.fail("not a regular expression: " + regex);
      }
      var matcher = pattern.matcher(interaction.reply());
      return matcher.find()
          ? EvalResult.fail("reply matches /" + regex + "/ at \"" + matcher.group() + "\"")
          : EvalResult.pass();
    }
  }

  private record AnswerLacksLuhnNumber(Pattern candidate, String name) implements Evaluator {

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var found = new ArrayList<String>();
      var matcher = candidate.matcher(interaction.reply());
      while (matcher.find()) {
        if (luhnValid(matcher.group())) found.add(matcher.group());
      }
      return found.isEmpty() ? EvalResult.pass() : EvalResult.fail("reply carries " + found);
    }

    /** The Luhn checksum over the digits of the candidate, separators skipped. */
    private static boolean luhnValid(String candidate) {
      var sum = 0;
      var doubled = false;

      for (var i = candidate.length() - 1; i >= 0; i--) {
        var character = candidate.charAt(i);
        if (character < '0' || character > '9') continue;

        var digit = character - '0';
        if (doubled) {
          digit *= 2;
          if (digit > 9) digit -= 9;
        }

        sum += digit;
        doubled = !doubled;
      }

      return sum % 10 == 0;
    }
  }

  /** A run of that many digits, with a single space or dash allowed between two of them. */
  private static Pattern luhnCandidate(int minDigits, int maxDigits) {
    return Pattern.compile(
        "\\b\\d(?:[ -]?\\d){" + (minDigits - 1) + "," + (maxDigits - 1) + "}\\b");
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
