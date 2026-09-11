/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.ToolCall;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The built-in evaluators.
 *
 * <p>Each factory returns an {@link Evaluator} for one check over the reply and the traced tool
 * calls. An evaluator reads only the evidence it names and is inconclusive when that evidence is
 * absent: a tool that was never called fails {@link #tools}, and {@link #toolArgument} is
 * inconclusive. Give the evaluators to an {@link EvalCase}, next to a {@link Judge} or a custom
 * evaluator.
 *
 * <p>The report prints an evaluator's results under its {@link EvalLabel}, and {@link
 * Gate#evaluatorRateAtLeast} refers to an evaluator by class. A custom evaluator's label carries
 * {@link #CUSTOM_PREFIX}, so it cannot collide with a built-in one. {@link #TARGET} and {@link
 * #SETUP} are reported by the runner when a case did not reach evaluation.
 */
public final class Evaluators {

  /** The namespace the report prints a custom evaluator under. */
  public static final String CUSTOM_PREFIX = "custom-eval:";

  /** The label of the result the runner reports when the agent call failed. */
  public static final String TARGET = "target";

  /**
   * The label of the result the runner reports when loading the case's recorded calls into the
   * stubs threw, so the agent was never called.
   */
  public static final String SETUP = "setup";

  private Evaluators() {}

  /**
   * The name the report prints results of this evaluator under: its {@link EvalLabel}, prefixed
   * with {@link #CUSTOM_PREFIX} unless the evaluator is one of the built-ins.
   *
   * @throws IllegalArgumentException when the class carries no {@link EvalLabel}
   */
  public static String label(Class<? extends Evaluator> evaluator) {
    var label = requireLabel(evaluator);
    return BuiltIn.class.isAssignableFrom(evaluator) ? label : CUSTOM_PREFIX + label;
  }

  /** The class's {@link EvalLabel}: present, not blank, and without the prefix the runner adds. */
  static String requireLabel(Class<? extends Evaluator> evaluator) {
    if (evaluator == null) throw new IllegalArgumentException("evaluator required");
    var label = evaluator.getAnnotation(EvalLabel.class);
    if (label == null)
      throw new IllegalArgumentException(
          evaluator.getName()
              + " carries no @EvalLabel; an evaluator is a named class annotated with it");
    if (label.value().isBlank())
      throw new IllegalArgumentException(evaluator.getName() + " has a blank @EvalLabel");
    if (label.value().startsWith(CUSTOM_PREFIX))
      throw new IllegalArgumentException(
          evaluator.getName()
              + " puts "
              + CUSTOM_PREFIX
              + " in its @EvalLabel; the runner adds the prefix");
    return label.value();
  }

  /** These tools must be called, in any order. Other calls are allowed. */
  public static Evaluator tools(String... names) {
    return new Tools(setOf(names));
  }

  /**
   * These tools must be called in this relative order. Calls to other tools may come between.
   * Inconclusive when one of them was never called.
   */
  public static Evaluator toolOrder(String... names) {
    return new ToolOrder(listOf(names));
  }

  /**
   * The named tool must be called with this argument value. Numbers compare by value. Inconclusive
   * when the tool was never called.
   */
  public static Evaluator toolArgument(String tool, String argument, Object value) {
    return new ToolArgument(tool, argument, value);
  }

  /**
   * The result of the named tool must contain this text, case-insensitively. Inconclusive when the
   * tool was never called or the trace carries no result for it.
   */
  public static Evaluator toolResult(String tool, String text) {
    return new ToolResult(tool, text);
  }

  /** None of these tools may be called. */
  public static Evaluator forbiddenTools(String... names) {
    return new ForbiddenTools(setOf(names));
  }

  /** The agent may make at most this many tool calls while answering. */
  public static Evaluator toolCallsAtMost(int calls) {
    return new ToolCallBudget(calls);
  }

  /**
   * The agent may make at most this many model calls while answering. Inconclusive when the trace
   * carried no model calls.
   */
  public static Evaluator modelCallsAtMost(int calls) {
    return new ModelCallBudget(calls);
  }

  /**
   * The turn may use at most this many tokens, input and output together. Inconclusive when no
   * model call reported tokens, as with a mocked model.
   */
  public static Evaluator tokensAtMost(long tokens) {
    return new TokenBudget(tokens);
  }

  /**
   * The agent command must complete within this time. Inconclusive when the trace carries no
   * timing.
   */
  public static Evaluator latencyAtMost(Duration latency) {
    return new LatencyBudget(latency);
  }

  /** The reply must contain every given text, case-insensitively. */
  public static Evaluator answerContains(String... texts) {
    return new AnswerContains(listOf(texts));
  }

  /** The reply must match the regular expression, anywhere in it. Anchor it for a full match. */
  public static Evaluator answerMatches(String regex) {
    return new AnswerMatches(regex);
  }

  /** The reply must not contain any of the given texts, case-insensitively. */
  public static Evaluator answerLacks(String... texts) {
    return new AnswerLacks(listOf(texts));
  }

  /** The reply must not match the regular expression anywhere in it. */
  public static Evaluator answerDoesNotMatch(String regex) {
    return new AnswerDoesNotMatch(regex);
  }

  /**
   * The reply must not contain a sequence of digits within this length range that passes the Luhn
   * checksum. Spaces and dashes are allowed between the digits.
   *
   * <p>The Luhn checksum validates many identifiers besides payment cards, and each has its own
   * length: an IMEI has 15 digits, a South African ID 13, a Canadian Social Insurance Number 9.
   */
  public static Evaluator answerLacksLuhnNumber(int minDigits, int maxDigits) {
    return new AnswerLacksLuhnNumber(minDigits, maxDigits);
  }

  /**
   * The reply must not contain a payment card number. A card number is a run of 13 to 19 digits,
   * spaces and dashes allowed between them, that passes the Luhn checksum.
   */
  public static Evaluator answerLacksPaymentCard() {
    return new AnswerLacksPaymentCard();
  }

  /** An evaluator this class ships, reported without {@link #CUSTOM_PREFIX}. */
  private sealed interface BuiltIn extends Evaluator {}

  /** {@link #tools}. */
  @EvalLabel("tools")
  public record Tools(Set<String> expected) implements BuiltIn {

    public Tools {
      expected = toolNames(expected);
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

  /** {@link #toolOrder}. */
  @EvalLabel("tool-order")
  public record ToolOrder(List<String> expected) implements BuiltIn {

    public ToolOrder {
      expected = List.copyOf(toolNames(expected));
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

  /** {@link #toolArgument}. */
  @EvalLabel("tool-arguments")
  public record ToolArgument(String tool, String argument, Object value) implements BuiltIn {

    public ToolArgument {
      requireName(tool, "tool");
      requireName(argument, "argument");
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

  /** {@link #toolResult}. */
  @EvalLabel("tool-results")
  public record ToolResult(String tool, String text) implements BuiltIn {

    public ToolResult {
      requireName(tool, "tool");
      if (text == null || text.isEmpty()) throw new IllegalArgumentException("text required");
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

  /** {@link #forbiddenTools}. */
  @EvalLabel("forbidden-tools")
  public record ForbiddenTools(Set<String> forbidden) implements BuiltIn {

    public ForbiddenTools {
      forbidden = toolNames(forbidden);
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var calls = interaction.toolCalls();
      var called = names(calls);
      var violated = forbidden.stream().filter(called::contains).toList();
      return violated.isEmpty() ? EvalResult.pass() : EvalResult.fail("called " + violated);
    }
  }

  /** {@link #toolCallsAtMost}. */
  @EvalLabel("tool-call-budget")
  public record ToolCallBudget(int limit) implements BuiltIn {

    public ToolCallBudget {
      if (limit < 0) throw new IllegalArgumentException("a budget is not negative");
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

  /** {@link #modelCallsAtMost}. */
  @EvalLabel("model-call-budget")
  public record ModelCallBudget(int limit) implements BuiltIn {

    public ModelCallBudget {
      if (limit < 1) throw new IllegalArgumentException("an answer takes at least one model call");
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

  /** {@link #tokensAtMost}. */
  @EvalLabel("token-budget")
  public record TokenBudget(long limit) implements BuiltIn {

    public TokenBudget {
      if (limit < 1) throw new IllegalArgumentException("a token budget is positive");
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

  /** {@link #latencyAtMost}. */
  @EvalLabel("latency-budget")
  public record LatencyBudget(Duration limit) implements BuiltIn {

    public LatencyBudget {
      if (limit == null || limit.isNegative() || limit.isZero())
        throw new IllegalArgumentException("a latency budget is positive");
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

  /** {@link #answerContains}. */
  @EvalLabel("answer-contains")
  public record AnswerContains(List<String> texts) implements BuiltIn {

    public AnswerContains {
      texts = nonBlankTexts(texts);
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

  /** {@link #answerMatches}. */
  @EvalLabel("answer-matches")
  public record AnswerMatches(String regex) implements BuiltIn {

    public AnswerMatches {
      if (regex == null) throw new IllegalArgumentException("regex required");
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

  /** {@link #answerLacks}. */
  @EvalLabel("answer-lacks")
  public record AnswerLacks(List<String> texts) implements BuiltIn {

    public AnswerLacks {
      texts = nonBlankTexts(texts);
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

  /** {@link #answerDoesNotMatch}. */
  @EvalLabel("answer-does-not-match")
  public record AnswerDoesNotMatch(String regex) implements BuiltIn {

    public AnswerDoesNotMatch {
      if (regex == null) throw new IllegalArgumentException("regex required");
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

  /** {@link #answerLacksLuhnNumber}. */
  @EvalLabel("answer-lacks-luhn-number")
  public record AnswerLacksLuhnNumber(int minDigits, int maxDigits) implements BuiltIn {

    public AnswerLacksLuhnNumber {
      if (minDigits < 2)
        throw new IllegalArgumentException("a Luhn number has at least two digits");
      if (maxDigits < minDigits)
        throw new IllegalArgumentException("maxDigits is below minDigits: " + maxDigits);
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      var found = new ArrayList<String>();
      var matcher = candidate().matcher(interaction.reply());
      while (matcher.find()) {
        if (luhnValid(matcher.group())) found.add(matcher.group());
      }
      return found.isEmpty() ? EvalResult.pass() : EvalResult.fail("reply carries " + found);
    }

    /** A run of that many digits, with a single space or dash allowed between two of them. */
    private Pattern candidate() {
      return Pattern.compile(
          "\\b\\d(?:[ -]?\\d){" + (minDigits - 1) + "," + (maxDigits - 1) + "}\\b");
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

  /** {@link #answerLacksPaymentCard}. */
  @EvalLabel("answer-lacks-payment-card")
  public record AnswerLacksPaymentCard() implements BuiltIn {

    private static final AnswerLacksLuhnNumber CARD_NUMBER = new AnswerLacksLuhnNumber(13, 19);

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      return CARD_NUMBER.evaluate(evalCase, interaction);
    }
  }

  /** The evaluator behind {@link Judge#scoringAtLeast}: asks the judge and holds the score. */
  @EvalLabel("judge")
  public record JudgeEvaluator(Judge judge, String criterion, double threshold) implements BuiltIn {

    public JudgeEvaluator {
      if (judge == null) throw new IllegalArgumentException("judge required");
      if (criterion == null || criterion.isBlank())
        throw new IllegalArgumentException("criterion required");
    }

    @Override
    public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
      if (interaction.reply().isBlank()) {
        return EvalResult.inconclusive(criterion + ": there is no reply to judge");
      }
      Judge.Verdict verdict;
      try {
        verdict = judge.decide(criterion, interaction);
      } catch (RuntimeException e) {
        return EvalResult.inconclusive(criterion + ": the judge failed: " + e.getMessage());
      }
      if (verdict == null) {
        return EvalResult.inconclusive(criterion + ": the judge gave no verdict");
      }
      var score = verdict.score();
      if (Double.isNaN(score) || score < 0 || score > 1) {
        return EvalResult.inconclusive(
            criterion + ": the judge scored " + score + ", which is not a share");
      }
      var detail =
          String.format(
              Locale.ROOT,
              "%s: scored %.2f, needed %.2f%s",
              criterion,
              score,
              threshold,
              verdict.reason().isEmpty() ? "" : " — " + verdict.reason());
      return score >= threshold ? EvalResult.pass(detail) : EvalResult.fail(detail);
    }
  }

  private static Set<String> setOf(String... names) {
    return names == null ? Set.of() : new LinkedHashSet<>(Arrays.asList(names));
  }

  private static List<String> listOf(String... items) {
    return items == null ? List.of() : Arrays.asList(items);
  }

  /** Non-blank, in the given order, without duplicates. */
  private static Set<String> toolNames(Collection<String> names) {
    if (names == null || names.isEmpty())
      throw new IllegalArgumentException("at least one tool name required");
    var set = new LinkedHashSet<String>();
    for (var name : names) {
      requireName(name, "tool");
      set.add(name);
    }
    return Collections.unmodifiableSet(set);
  }

  /** Non-blank, in the given order. */
  private static List<String> nonBlankTexts(List<String> texts) {
    if (texts == null || texts.isEmpty())
      throw new IllegalArgumentException("at least one text required");
    for (var text : texts) {
      if (text == null || text.isBlank()) throw new IllegalArgumentException("text required");
    }
    return List.copyOf(texts);
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
