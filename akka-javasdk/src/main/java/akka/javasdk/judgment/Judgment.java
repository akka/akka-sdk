/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.judgment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The answers of a judgment model to the questions of one request, see {@link JudgmentClient}. Read
 * each answer with {@link #answer(Question)}.
 *
 * @param model the model version that answered. Log it with the answers, because the thresholds you
 *     compare answers against depend on the model version.
 * @param answers the answer per question key
 */
public record Judgment(String model, Map<String, Answer> answers) {

  public Judgment {
    Objects.requireNonNull(model, "model");
    answers =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(answers, "answers")));
  }

  /**
   * The answer to the given question.
   *
   * @throws IllegalArgumentException when there is no answer for the key of the question, or the
   *     answer has a different type than the question
   */
  public <A extends Answer> A answer(Question<A> question) {
    Answer answer = answers.get(question.key());
    if (answer == null)
      throw new IllegalArgumentException("No answer for question [" + question.key() + "]");
    boolean matches =
        switch (question) {
          case Question.Choice choice -> answer instanceof ChoiceAnswer;
          case Question.Score score -> answer instanceof ScoreAnswer;
          case Question.YesNo yesNo -> answer instanceof YesNoAnswer;
        };
    if (!matches)
      throw new IllegalArgumentException(
          "The answer for question ["
              + question.key()
              + "] is a ["
              + answer.getClass().getSimpleName()
              + "], which does not match the question type");
    @SuppressWarnings("unchecked")
    A typed = (A) answer;
    return typed;
  }
}
