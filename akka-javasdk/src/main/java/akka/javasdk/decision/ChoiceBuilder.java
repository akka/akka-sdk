/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.decision;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder stages for choice questions. Start with {@link Question#choice(String)}. The stages only
 * offer {@code build} once the question is valid.
 */
public final class ChoiceBuilder {

  private ChoiceBuilder() {}

  /** A choice question without options. Add the first option. */
  public static final class NoOptions {
    private final String instructions;

    NoOptions(String instructions) {
      this.instructions = instructions;
    }

    /**
     * Add the first option.
     *
     * @param key the key returned in {@link Answer.Choice#selected()} when the model picks this
     *     option
     * @param description what the option covers
     */
    public OneOption option(String key, String description) {
      return new OneOption(instructions, new Question.Choice.Option(key, description));
    }
  }

  /** A choice question with one option. Add the second option. */
  public static final class OneOption {
    private final String instructions;
    private final Question.Choice.Option first;

    OneOption(String instructions, Question.Choice.Option first) {
      this.instructions = instructions;
      this.first = first;
    }

    /**
     * Add the second option.
     *
     * @param key the key returned in {@link Answer.Choice#selected()} when the model picks this
     *     option
     * @param description what the option covers
     */
    public Ready option(String key, String description) {
      return new Ready(instructions, List.of(first, new Question.Choice.Option(key, description)));
    }
  }

  /** A choice question with at least two options. Add more options or build the question. */
  public static final class Ready {
    private final String instructions;
    private final List<Question.Choice.Option> options;

    Ready(String instructions, List<Question.Choice.Option> options) {
      this.instructions = instructions;
      this.options = options;
    }

    /**
     * Add another option.
     *
     * @param key the key returned in {@link Answer.Choice#selected()} when the model picks this
     *     option
     * @param description what the option covers
     */
    public Ready option(String key, String description) {
      var newOptions = new ArrayList<>(options);
      newOptions.add(new Question.Choice.Option(key, description));
      return new Ready(instructions, newOptions);
    }

    /**
     * Build the question with the given key. The key identifies the question and its answer, so it
     * must be the same everywhere the question is built, also across restarts and services.
     *
     * @throws IllegalArgumentException when two options have the same key
     */
    public Question.Choice build(String key) {
      return new Question.Choice(key, instructions, options);
    }
  }
}
