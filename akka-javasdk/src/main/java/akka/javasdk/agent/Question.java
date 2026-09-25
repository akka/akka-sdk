/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A question in a structured judgment request, see {@link Agent.Effect.Builder#judgment()}. The
 * model answers every question in a request against the same state and returns a probability
 * distribution per question.
 *
 * <p>{@link Choice} picks one option from a set. {@link Score} places the state on an ordered
 * scale. {@link YesNo} answers with the probability of yes.
 */
public sealed interface Question permits Question.Choice, Question.Score, Question.YesNo {

  /** What the model decides about the state. */
  String instructions();

  /** A choice question without options. Add options with {@link Choice#option}. */
  static Choice choice(String instructions) {
    return new Choice(instructions, List.of());
  }

  /**
   * A score question.
   *
   * @param levels the levels in order from lowest to highest, 2 to 10 levels
   */
  static Score score(String instructions, String... levels) {
    return new Score(instructions, List.of(levels));
  }

  /** A yes or no question. */
  static YesNo yesNo(String instructions) {
    return new YesNo(instructions, Optional.empty(), Optional.empty());
  }

  /**
   * A yes or no question with a description of each answer.
   *
   * @param whenYes what counts as yes
   * @param whenNo what counts as no
   */
  static YesNo yesNo(String instructions, String whenYes, String whenNo) {
    return new YesNo(
        instructions,
        Optional.of(Objects.requireNonNull(whenYes, "whenYes")),
        Optional.of(Objects.requireNonNull(whenNo, "whenNo")));
  }

  private static void requireInstructions(String instructions) {
    if (instructions == null || instructions.isBlank())
      throw new IllegalArgumentException("Question instructions must not be blank");
  }

  private static void requireKey(String key) {
    if (key == null || key.isBlank())
      throw new IllegalArgumentException("Option key must not be blank");
  }

  /**
   * Pick one option. The answer is a {@link Judgment.ChoiceAnswer} with the winning option key and
   * a probability per option.
   *
   * @param options the options in order, unique keys, at most 255
   */
  record Choice(String instructions, List<Option> options) implements Question {

    /** The maximum number of options in one choice question. */
    public static final int MAX_OPTIONS = 255;

    /**
     * One option of a choice question.
     *
     * @param key the option key, returned as the answer
     * @param description what the option covers, empty for a bare label
     */
    public record Option(String key, Optional<String> description) {
      public Option {
        requireKey(key);
        Objects.requireNonNull(description, "description");
        if (description.isPresent() && description.get().isBlank())
          throw new IllegalArgumentException("Option description must not be blank");
      }
    }

    public Choice {
      requireInstructions(instructions);
      Objects.requireNonNull(options, "options");
      Set<String> keys = new HashSet<>();
      for (Option option : options) {
        if (option == null) throw new IllegalArgumentException("Options must not contain null");
        if (!keys.add(option.key()))
          throw new IllegalArgumentException("Duplicate option key [" + option.key() + "]");
      }
      if (options.size() > MAX_OPTIONS)
        throw new IllegalArgumentException(
            "A choice question can have at most " + MAX_OPTIONS + " options");
      options = List.copyOf(options);
    }

    /** The same question with one more option. */
    public Choice option(String key, String description) {
      return addOption(
          new Option(key, Optional.of(Objects.requireNonNull(description, "description"))));
    }

    /** The same question with one more option, given as a bare label. */
    public Choice option(String key) {
      return addOption(new Option(key, Optional.empty()));
    }

    private Choice addOption(Option option) {
      List<Option> updated = new ArrayList<>(options);
      updated.add(option);
      return new Choice(instructions, updated);
    }
  }

  /**
   * Place the state on an ordered scale. The answer is a {@link Judgment.ScoreAnswer} with a
   * position along the levels and a probability per level.
   *
   * @param levels the levels in order from lowest to highest, 2 to 10 levels
   */
  record Score(String instructions, List<String> levels) implements Question {
    public Score {
      requireInstructions(instructions);
      Objects.requireNonNull(levels, "levels");
      for (String level : levels) {
        if (level == null || level.isBlank())
          throw new IllegalArgumentException("Score levels must not be blank");
      }
      if (levels.size() < 2 || levels.size() > 10)
        throw new IllegalArgumentException("A score question must have 2 to 10 levels");
      levels = List.copyOf(levels);
    }
  }

  /**
   * Answer yes or no. The answer is a {@link Judgment.YesNoAnswer} with the probability of yes.
   *
   * @param whenYes what counts as yes
   * @param whenNo what counts as no
   */
  record YesNo(String instructions, Optional<String> whenYes, Optional<String> whenNo)
      implements Question {
    public YesNo {
      requireInstructions(instructions);
      Objects.requireNonNull(whenYes, "whenYes");
      Objects.requireNonNull(whenNo, "whenNo");
      if (whenYes.isPresent() && whenYes.get().isBlank())
        throw new IllegalArgumentException("whenYes must not be blank");
      if (whenNo.isPresent() && whenNo.get().isBlank())
        throw new IllegalArgumentException("whenNo must not be blank");
    }
  }
}
