/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.judgment;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A question for a judgment model, see {@link JudgmentClient}. The type parameter is the type of
 * the answer.
 *
 * <p>Create a question with {@link #choice(String)}, {@link #score(String)} or {@link
 * #yesNo(String)}. A question is immutable. To ask a variant of an existing question, pass it to
 * the factory of the same type, for example {@link #choice(Question)}, and build it with a new key.
 *
 * <p>The key identifies the question and its answer in one request. {@code build()} generates a
 * random key. {@code build(key)} uses the given key. All questions in one request must have
 * different keys.
 *
 * @param <A> the type of the answer
 */
public sealed interface Question<A extends Answer>
    permits Question.Choice, Question.Score, Question.YesNo {

  /** The key that identifies the question and its answer in one request. */
  String key();

  /** What the model should judge. */
  String instructions();

  /**
   * Start a question that picks one of at least two options.
   *
   * @param instructions what the model should choose, for example "Which team should handle this
   *     ticket?"
   */
  static ChoiceBuilder.NoOptions choice(String instructions) {
    requireText(instructions, "instructions");
    return new ChoiceBuilder.NoOptions(instructions);
  }

  /** Start a new choice question with the instructions and options of an existing one. */
  static ChoiceBuilder.Ready choice(Question<ChoiceAnswer> template) {
    var choice = (Choice) template;
    return new ChoiceBuilder.Ready(choice.instructions(), choice.options());
  }

  /**
   * Start a question that places the state on an ordered scale of at least two levels.
   *
   * @param instructions what the model should rate, for example "How severe is the reported issue?"
   */
  static ScoreBuilder.NoLevels score(String instructions) {
    requireText(instructions, "instructions");
    return new ScoreBuilder.NoLevels(instructions);
  }

  /** Start a new score question with the instructions and levels of an existing one. */
  static ScoreBuilder.Ready score(Question<ScoreAnswer> template) {
    var score = (Score) template;
    return new ScoreBuilder.Ready(score.instructions(), score.levels());
  }

  /**
   * Start a question that the model answers with the probability of yes.
   *
   * @param instructions the question, for example "Does the customer need a reply today?"
   */
  static ChoiceBuilder.YesNo yesNo(String instructions) {
    requireText(instructions, "instructions");
    return new ChoiceBuilder.YesNo(instructions, Optional.empty(), Optional.empty());
  }

  /** Start a new yes or no question with the instructions and criteria of an existing one. */
  static ChoiceBuilder.YesNo yesNo(Question<YesNoAnswer> template) {
    var yesNo = (YesNo) template;
    return new ChoiceBuilder.YesNo(yesNo.instructions(), yesNo.whenYes(), yesNo.whenNo());
  }

  /**
   * Pick one option. Answered with a {@link ChoiceAnswer}.
   *
   * @param options at least two options with different keys, in the order given to the model
   */
  record Choice(String key, String instructions, List<Option> options)
      implements Question<ChoiceAnswer> {

    public Choice {
      requireText(key, "key");
      requireText(instructions, "instructions");
      options = List.copyOf(Objects.requireNonNull(options, "options"));
      if (options.size() < 2)
        throw new IllegalArgumentException(
            "A choice question needs at least two options, got [" + options.size() + "]");
      var optionKeys = new HashSet<String>();
      for (var option : options) {
        if (!optionKeys.add(option.key()))
          throw new IllegalArgumentException("Duplicate option key [" + option.key() + "]");
      }
    }

    /**
     * One option of a choice question.
     *
     * @param key the key returned in {@link ChoiceAnswer#selected()} when the model picks this
     *     option
     * @param description what the option covers
     */
    public record Option(String key, String description) {
      public Option {
        requireText(key, "key");
        requireText(description, "description");
      }
    }
  }

  /**
   * Place the state on an ordered scale. Answered with a {@link ScoreAnswer}.
   *
   * @param levels at least two level descriptions, from lowest to highest
   */
  record Score(String key, String instructions, List<String> levels)
      implements Question<ScoreAnswer> {

    public Score {
      requireText(key, "key");
      requireText(instructions, "instructions");
      levels = List.copyOf(Objects.requireNonNull(levels, "levels"));
      if (levels.size() < 2)
        throw new IllegalArgumentException(
            "A score question needs at least two levels, got [" + levels.size() + "]");
      levels.forEach(level -> requireText(level, "level"));
    }
  }

  /**
   * A yes or no question. Answered with a {@link YesNoAnswer}.
   *
   * @param whenYes what counts as yes, if given
   * @param whenNo what counts as no, if given
   */
  record YesNo(String key, String instructions, Optional<String> whenYes, Optional<String> whenNo)
      implements Question<YesNoAnswer> {

    public YesNo {
      requireText(key, "key");
      requireText(instructions, "instructions");
      Objects.requireNonNull(whenYes, "whenYes").ifPresent(text -> requireText(text, "whenYes"));
      Objects.requireNonNull(whenNo, "whenNo").ifPresent(text -> requireText(text, "whenNo"));
    }
  }

  private static void requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) throw new IllegalArgumentException("[" + name + "] must not be blank");
  }
}
