/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.judgment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Builder stages for score questions. Start with {@link Question#score(String)}. The stages only
 * offer {@code build} once the question has levels.
 */
public final class ScoreBuilder {

  private ScoreBuilder() {}

  /** A score question without levels. Define the levels. */
  public static final class NoLevels {
    private final String instructions;

    NoLevels(String instructions) {
      this.instructions = instructions;
    }

    /**
     * Define the levels of the scale, from lowest to highest.
     *
     * @param lowest the description of the lowest level
     * @param next the description of the next level
     * @param higher the descriptions of further levels, in increasing order
     */
    public Ready levels(String lowest, String next, String... higher) {
      var levels = new ArrayList<String>(2 + higher.length);
      levels.add(lowest);
      levels.add(next);
      Collections.addAll(levels, higher);
      return new Ready(instructions, levels);
    }
  }

  /** A score question with levels. Build the question. */
  public static final class Ready {
    private final String instructions;
    private final List<String> levels;

    Ready(String instructions, List<String> levels) {
      this.instructions = instructions;
      this.levels = levels;
    }

    /** Build the question with a random key. */
    public Question.Score build() {
      return build(UUID.randomUUID().toString());
    }

    /** Build the question with the given key. */
    public Question.Score build(String key) {
      return new Question.Score(key, instructions, levels);
    }
  }
}
