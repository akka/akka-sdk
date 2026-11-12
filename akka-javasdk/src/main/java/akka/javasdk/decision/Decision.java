/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.decision;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The answers of a decision model to the questions of one request, see {@link DecisionModelClient}.
 * Read each answer with {@link #answer(Question)}. A decision can be stored in component state and
 * returned from endpoints, the JSON representation round-trips.
 *
 * @param model the model version that answered. Log it with the answers, because the thresholds you
 *     compare answers against depend on the model version.
 * @param answers the answer per question key
 */
public record Decision(String model, Map<String, Answer> answers) {

  public Decision {
    Objects.requireNonNull(model, "model");
    answers =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(answers, "answers")));
  }

  /**
   * The answer to the given question.
   *
   * @throws IllegalArgumentException when there is no answer for the key of the question, or the
   *     answer does not match the type, options or levels of the question
   */
  public <A extends Answer> A answer(Question<A> question) {
    Answer answer = answers.get(question.key());
    if (answer == null)
      throw new IllegalArgumentException(
          "No answer for question ["
              + question.key()
              + "], the decision has answers for "
              + answers.keySet());
    switch (question) {
      case Question.Choice choice -> checkChoice(choice, answer);
      case Question.Score score -> checkScore(score, answer);
      case Question.YesNo yesNo -> checkType(yesNo, answer, answer instanceof Answer.YesNo);
    }
    @SuppressWarnings("unchecked")
    A typed = (A) answer;
    return typed;
  }

  private void checkChoice(Question.Choice question, Answer answer) {
    checkType(question, answer, answer instanceof Answer.Choice);
    var choice = (Answer.Choice) answer;
    var optionKeys =
        question.options().stream().map(Question.Choice.Option::key).collect(Collectors.toSet());
    if (!optionKeys.contains(choice.selected()))
      throw new IllegalArgumentException(
          "The answer for question ["
              + question.key()
              + "] selects ["
              + choice.selected()
              + "], which is not an option of the question");
    if (!optionKeys.containsAll(choice.probabilities().keySet()))
      throw new IllegalArgumentException(
          "The answer for question ["
              + question.key()
              + "] has probabilities for "
              + choice.probabilities().keySet()
              + ", which are not all options of the question");
  }

  private void checkScore(Question.Score question, Answer answer) {
    checkType(question, answer, answer instanceof Answer.Score);
    var score = (Answer.Score) answer;
    if (!score.probabilities().isEmpty()
        && score.probabilities().size() != question.levels().size())
      throw new IllegalArgumentException(
          "The answer for question ["
              + question.key()
              + "] has ["
              + score.probabilities().size()
              + "] level probabilities, but the question has ["
              + question.levels().size()
              + "] levels");
  }

  private void checkType(Question<?> question, Answer answer, boolean matches) {
    if (!matches)
      throw new IllegalArgumentException(
          "The answer for question ["
              + question.key()
              + "] is a ["
              + answer.getClass().getSimpleName()
              + "] answer, which does not match the question type");
  }
}
