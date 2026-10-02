/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.judgment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The answer to a {@link Question.Choice}.
 *
 * @param selected the key of the option with the highest probability
 * @param confidence how peaked the distribution over the options is, from 0 to 1
 * @param probabilities the probability per option key
 */
public record ChoiceAnswer(String selected, double confidence, Map<String, Double> probabilities)
    implements Answer {

  public ChoiceAnswer {
    Objects.requireNonNull(selected, "selected");
    probabilities =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(probabilities, "probabilities")));
  }
}
