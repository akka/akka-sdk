/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.decision;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The answer to one {@link Question}. The type of the answer follows the type of the question. Read
 * an answer with {@link Decision#answer(Question)}.
 *
 * <p>The JSON representation of an answer carries a {@code type} tag, so a {@link Decision} that a
 * component stores or returns can be read back.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Answer.Choice.class, name = "choice"),
  @JsonSubTypes.Type(value = Answer.Score.class, name = "score"),
  @JsonSubTypes.Type(value = Answer.YesNo.class, name = "yes-no")
})
public sealed interface Answer {

  /**
   * The answer to a {@link Question.Choice}.
   *
   * @param selected the key of the option with the highest probability
   * @param confidence how peaked the distribution over the options is, from 0 to 1
   * @param probabilities the probability per option key
   */
  record Choice(String selected, double confidence, Map<String, Double> probabilities)
      implements Answer {

    public Choice {
      Objects.requireNonNull(selected, "selected");
      probabilities =
          Collections.unmodifiableMap(
              new LinkedHashMap<>(Objects.requireNonNull(probabilities, "probabilities")));
    }
  }

  /**
   * The answer to a {@link Question.Score}.
   *
   * @param value the position along the levels, counted from 0 for the lowest level. The value can
   *     fall between two levels.
   * @param confidence how peaked the distribution over the levels is, from 0 to 1
   * @param probabilities the probability per level, in the order of the levels
   */
  record Score(double value, double confidence, List<Double> probabilities) implements Answer {

    public Score {
      probabilities = List.copyOf(Objects.requireNonNull(probabilities, "probabilities"));
    }
  }

  /**
   * The answer to a {@link Question.YesNo}. Compare the probability with a threshold that fits the
   * cost of a wrong answer in your use case.
   *
   * @param probability the probability that the answer is yes, from 0 to 1
   */
  record YesNo(double probability) implements Answer {}
}
