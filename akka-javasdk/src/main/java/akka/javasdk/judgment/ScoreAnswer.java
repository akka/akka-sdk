/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.judgment;

import java.util.List;
import java.util.Objects;

/**
 * The answer to a {@link Question.Score}.
 *
 * @param value the position along the levels, counted from 0 for the lowest level. The value can
 *     fall between two levels.
 * @param confidence how peaked the distribution over the levels is, from 0 to 1
 * @param probabilities the probability per level, in the order of the levels
 */
public record ScoreAnswer(double value, double confidence, List<Double> probabilities)
    implements Answer {

  public ScoreAnswer {
    probabilities = List.copyOf(Objects.requireNonNull(probabilities, "probabilities"));
  }
}
