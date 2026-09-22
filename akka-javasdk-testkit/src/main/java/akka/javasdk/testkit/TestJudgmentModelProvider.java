/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.japi.Pair;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Judgment;
import akka.javasdk.agent.JudgmentModelProvider;
import akka.javasdk.agent.JudgmentRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A {@link JudgmentModelProvider} for tests that answers structured judgment requests without a
 * model. Register it for an agent with {@link TestKit.Settings#withJudgmentModelProvider(Class,
 * JudgmentModelProvider)} and define the answers with {@link #fixedAnswers} or {@link #whenState}.
 *
 * <p>The most recently added matching rule wins. A request that matches no rule fails with {@link
 * MissingJudgmentResponseException}.
 */
public final class TestJudgmentModelProvider implements JudgmentModelProvider.Custom {

  /** Thrown when a request matches none of the configured rules. */
  public static class MissingJudgmentResponseException extends RuntimeException {
    public MissingJudgmentResponseException(JudgmentRequest request) {
      super(
          "No judgment configured for state ["
              + request.stateAsText()
              + "] with questions "
              + request.questions().keySet());
    }
  }

  private static final Agent.TokenUsage NO_TOKENS = new Agent.TokenUsage(0, 0);

  private final List<Pair<Predicate<JudgmentRequest>, Function<JudgmentRequest, Judgment>>> rules =
      new CopyOnWriteArrayList<>();

  @Override
  public String modelName() {
    return "test-judgment-model";
  }

  @Override
  public Judgment judge(JudgmentRequest request) {
    for (var rule : rules) {
      if (rule.first().test(request)) return rule.second().apply(request);
    }
    throw new MissingJudgmentResponseException(request);
  }

  /** Answers for every request. */
  public void fixedAnswers(Map<String, Judgment.Answer> answers) {
    whenRequest(request -> true).reply(answers);
  }

  /** Answers computed from each request. */
  public void fixedAnswers(Function<JudgmentRequest, Map<String, Judgment.Answer>> handler) {
    whenRequest(request -> true).reply(handler);
  }

  /** A rule for requests whose state text equals the given text. */
  public WhenClause whenState(String state) {
    return whenRequest(request -> state.equals(request.stateAsText()));
  }

  /** A rule for requests whose state text matches the predicate. */
  public WhenClause whenState(Predicate<String> predicate) {
    return whenRequest(request -> predicate.test(request.stateAsText()));
  }

  /** A rule for requests that match the predicate. */
  public WhenClause whenRequest(Predicate<JudgmentRequest> predicate) {
    return new WhenClause(predicate);
  }

  /** Removes all rules. */
  public void reset() {
    rules.clear();
  }

  /** A choice answer with all probability on the chosen option. */
  public static Judgment.ChoiceAnswer choice(String option) {
    return new Judgment.ChoiceAnswer(option, Map.of(option, 1.0), 1.0);
  }

  /** A score answer without a legend and full confidence. */
  public static Judgment.ScoreAnswer score(double score) {
    return new Judgment.ScoreAnswer(score, List.of(), List.of(), 1.0);
  }

  /** A yes answer with probability 1. */
  public static Judgment.YesNoAnswer yes() {
    return new Judgment.YesNoAnswer(1.0);
  }

  /** A no answer with probability 0. */
  public static Judgment.YesNoAnswer no() {
    return new Judgment.YesNoAnswer(0.0);
  }

  /** Completes a rule started with {@link #whenState} or {@link #whenRequest}. */
  public final class WhenClause {
    private final Predicate<JudgmentRequest> predicate;

    private WhenClause(Predicate<JudgmentRequest> predicate) {
      this.predicate = predicate;
    }

    /** Reply with these answers and no token usage. */
    public void reply(Map<String, Judgment.Answer> answers) {
      reply(answers, NO_TOKENS);
    }

    /** Reply with these answers and the given token usage. */
    public void reply(Map<String, Judgment.Answer> answers, Agent.TokenUsage tokenUsage) {
      addRule(request -> new Judgment(answers, modelName(), tokenUsage));
    }

    /** Reply with answers computed from the request. */
    public void reply(Function<JudgmentRequest, Map<String, Judgment.Answer>> handler) {
      addRule(request -> new Judgment(handler.apply(request), modelName(), NO_TOKENS));
    }

    /** Fail matching requests with this exception. */
    public void failWith(RuntimeException error) {
      addRule(
          request -> {
            throw error;
          });
    }

    private void addRule(Function<JudgmentRequest, Judgment> response) {
      rules.addFirst(new Pair<>(predicate, response));
    }
  }
}
