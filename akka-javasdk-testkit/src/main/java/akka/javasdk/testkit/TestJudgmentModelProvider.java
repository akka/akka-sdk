/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.judgment.Answer;
import akka.javasdk.judgment.ChoiceAnswer;
import akka.javasdk.judgment.Judgment;
import akka.javasdk.judgment.JudgmentModelProvider;
import akka.javasdk.judgment.Question;
import akka.javasdk.judgment.ScoreAnswer;
import akka.javasdk.judgment.YesNoAnswer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * A {@link JudgmentModelProvider} for tests that answers judgment requests without a model.
 * Register it with {@link TestKit.Settings#withJudgmentModelProvider(JudgmentModelProvider)} and
 * define the answers per state.
 *
 * <pre>{@code
 * testJudgmentModel
 *     .whenState(ticket)
 *     .choice(ROUTE, "billing")
 *     .score(SEVERITY, 2)
 *     .yesNo(URGENT, 0.9);
 * }</pre>
 *
 * <p>The most recently added rule that matches the request answers it. A request that matches no
 * rule, or asks a question that the matching rule does not answer, fails with {@link
 * MissingJudgmentResponseException}.
 */
public final class TestJudgmentModelProvider implements JudgmentModelProvider.Custom {

  /** The model name in every judgment from this provider. */
  public static final String MODEL_NAME = "test-judgment-model";

  /** Thrown when no rule answers a request. */
  public static class MissingJudgmentResponseException extends RuntimeException {
    public MissingJudgmentResponseException(String message) {
      super(message);
    }
  }

  private final List<Rule> rules = new CopyOnWriteArrayList<>();

  @Override
  public Judgment judge(Request request) {
    for (var rule : rules) {
      if (rule.predicate.test(request)) return rule.answer(request);
    }
    throw new MissingJudgmentResponseException(
        "No judgment configured for a request of type ["
            + request.state().getClass().getName()
            + "] with questions "
            + request.questions().stream().map(Question::key).toList());
  }

  /** A rule for requests about a state that equals the given state. */
  public Rule whenState(Object state) {
    Objects.requireNonNull(state, "state");
    return whenRequest(request -> state.equals(request.state()));
  }

  /** A rule for every request. */
  public Rule always() {
    return whenRequest(request -> true);
  }

  /** A rule for requests that match the predicate. */
  public Rule whenRequest(Predicate<Request> predicate) {
    var rule = new Rule(predicate);
    rules.addFirst(rule);
    return rule;
  }

  /** Remove all rules. */
  public void reset() {
    rules.clear();
  }

  /**
   * The answers to the requests that match one predicate. Add an answer per question. Answers for
   * questions that the request does not ask are left out of the judgment.
   */
  public static final class Rule {
    private final Predicate<Request> predicate;
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private volatile RuntimeException failure;

    private Rule(Predicate<Request> predicate) {
      this.predicate = predicate;
    }

    /**
     * Answer the question with the given selected option. The selected option gets probability 1
     * and the other options probability 0.
     *
     * @throws IllegalArgumentException when the question has no option with that key
     */
    public Rule choice(Question<ChoiceAnswer> question, String selected) {
      var choice = (Question.Choice) question;
      var probabilities = new LinkedHashMap<String, Double>();
      choice.options().forEach(option -> probabilities.put(option.key(), 0.0));
      if (!probabilities.containsKey(selected))
        throw new IllegalArgumentException(
            "Question [" + question.key() + "] has no option [" + selected + "]");
      probabilities.put(selected, 1.0);
      return answer(question, new ChoiceAnswer(selected, 1.0, probabilities));
    }

    /**
     * Answer the question with the given position along the levels, counted from 0. A value between
     * two levels splits the probability between them.
     *
     * @throws IllegalArgumentException when the value is outside the levels of the question
     */
    public Rule score(Question<ScoreAnswer> question, double value) {
      int levels = ((Question.Score) question).levels().size();
      if (value < 0 || value > levels - 1)
        throw new IllegalArgumentException(
            "Score ["
                + value
                + "] is outside the levels of question ["
                + question.key()
                + "], 0 to "
                + (levels - 1));
      var probabilities = new ArrayList<>(Collections.nCopies(levels, 0.0));
      int lower = (int) Math.floor(value);
      double fraction = value - lower;
      probabilities.set(lower, 1.0 - fraction);
      if (fraction > 0) probabilities.set(lower + 1, fraction);
      return answer(question, new ScoreAnswer(value, 1.0, probabilities));
    }

    /**
     * Answer the question with the given probability of yes.
     *
     * @throws IllegalArgumentException when the probability is outside 0 to 1
     */
    public Rule yesNo(Question<YesNoAnswer> question, double probability) {
      if (probability < 0 || probability > 1)
        throw new IllegalArgumentException("Probability [" + probability + "] is outside 0 to 1");
      return answer(question, new YesNoAnswer(probability));
    }

    /** Answer the question with the given answer. */
    public <A extends Answer> Rule answer(Question<A> question, A answer) {
      answers.put(question.key(), Objects.requireNonNull(answer, "answer"));
      return this;
    }

    /** Fail matching requests with this exception. */
    public void failWith(RuntimeException error) {
      this.failure = Objects.requireNonNull(error, "error");
    }

    private Judgment answer(Request request) {
      if (failure != null) throw failure;
      var result = new LinkedHashMap<String, Answer>();
      var missing = new ArrayList<String>();
      for (var question : request.questions()) {
        var answer = answers.get(question.key());
        if (answer == null) missing.add(question.key());
        else result.put(question.key(), answer);
      }
      if (!missing.isEmpty())
        throw new MissingJudgmentResponseException(
            "The matching rule has no answer for questions " + missing);
      return new Judgment(MODEL_NAME, result);
    }
  }
}
