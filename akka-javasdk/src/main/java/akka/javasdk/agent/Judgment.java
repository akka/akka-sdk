/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The answers to a structured judgment request, see {@link Agent.Effect.Builder#judgment()}.
 *
 * <p>Each answer is keyed by the question key given to the builder. Use {@link #choice}, {@link
 * #score} and {@link #yesNo} to read an answer as its type.
 *
 * @param answers the answer per question key, in request order
 * @param model the model that answered, empty when unknown
 * @param tokenUsage the tokens consumed by the call, zero when unknown
 */
public record Judgment(Map<String, Answer> answers, String model, Agent.TokenUsage tokenUsage) {

  public Judgment {
    Objects.requireNonNull(answers, "answers");
    answers = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
    model = model == null ? "" : model;
    tokenUsage = tokenUsage == null ? new Agent.TokenUsage(0, 0) : tokenUsage;
  }

  /** The answer to one question. The type follows the type of the question. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = ChoiceAnswer.class, name = "choice"),
    @JsonSubTypes.Type(value = ScoreAnswer.class, name = "score"),
    @JsonSubTypes.Type(value = YesNoAnswer.class, name = "noul")
  })
  public sealed interface Answer permits ChoiceAnswer, ScoreAnswer, YesNoAnswer {}

  /**
   * The answer to a {@link Question.Choice}.
   *
   * @param selected the key of the selected option
   * @param probabilities the probability per option key
   * @param confidence how peaked the distribution is, from 0 to 1
   */
  public record ChoiceAnswer(String selected, Map<String, Double> probabilities, double confidence)
      implements Answer {
    public ChoiceAnswer {
      Objects.requireNonNull(selected, "selected");
      Objects.requireNonNull(probabilities, "probabilities");
      probabilities = Collections.unmodifiableMap(new LinkedHashMap<>(probabilities));
    }
  }

  /**
   * The answer to a {@link Question.Score}.
   *
   * @param value the position along the levels, counted from 0, which can fall between two levels
   * @param legend the level descriptions by index
   * @param probabilities the probability per level index
   * @param confidence how peaked the distribution is, from 0 to 1
   */
  public record ScoreAnswer(
      double value, List<String> legend, List<Double> probabilities, double confidence)
      implements Answer {
    public ScoreAnswer {
      legend = List.copyOf(Objects.requireNonNull(legend, "legend"));
      probabilities = List.copyOf(Objects.requireNonNull(probabilities, "probabilities"));
    }
  }

  /**
   * The answer to a {@link Question.YesNo}.
   *
   * @param probability the probability that the answer is yes
   */
  public record YesNoAnswer(double probability) implements Answer {

    /** True when the probability of yes is at least 0.5. */
    @JsonIgnore
    public boolean isYes() {
      return isYes(0.5);
    }

    /** True when the probability of yes is at least the threshold. */
    public boolean isYes(double threshold) {
      return probability >= threshold;
    }
  }

  /**
   * The answer to the choice question with this key.
   *
   * @throws IllegalArgumentException when there is no such question or it is not a choice
   */
  public ChoiceAnswer choice(String key) {
    return answer(key, ChoiceAnswer.class);
  }

  /**
   * The answer to the score question with this key.
   *
   * @throws IllegalArgumentException when there is no such question or it is not a score
   */
  public ScoreAnswer score(String key) {
    return answer(key, ScoreAnswer.class);
  }

  /**
   * The answer to the yes or no question with this key.
   *
   * @throws IllegalArgumentException when there is no such question or it is not yes or no
   */
  public YesNoAnswer yesNo(String key) {
    return answer(key, YesNoAnswer.class);
  }

  private <T extends Answer> T answer(String key, Class<T> type) {
    Answer answer = answers.get(key);
    if (answer == null) throw new IllegalArgumentException("No answer for question [" + key + "]");
    if (!type.isInstance(answer))
      throw new IllegalArgumentException(
          "The answer for question ["
              + key
              + "] is a "
              + answer.getClass().getSimpleName()
              + ", not a "
              + type.getSimpleName());
    return type.cast(answer);
  }
}
