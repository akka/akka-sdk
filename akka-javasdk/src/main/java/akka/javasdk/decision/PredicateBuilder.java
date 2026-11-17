/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.decision;

import java.util.Optional;

/**
 * Builder for predicate questions. Start with {@link Question#predicate(String)}. Optionally
 * describe what counts as true and what counts as false, then build the question.
 */
public final class PredicateBuilder {
  private final String instructions;
  private final Optional<String> whenTrue;
  private final Optional<String> whenFalse;

  PredicateBuilder(String instructions, Optional<String> whenTrue, Optional<String> whenFalse) {
    this.instructions = instructions;
    this.whenTrue = whenTrue;
    this.whenFalse = whenFalse;
  }

  /** Describe what counts as true. */
  public PredicateBuilder whenTrue(String description) {
    return new PredicateBuilder(instructions, Optional.of(description), whenFalse);
  }

  /** Describe what counts as false. */
  public PredicateBuilder whenFalse(String description) {
    return new PredicateBuilder(instructions, whenTrue, Optional.of(description));
  }

  /**
   * Build the question with the given key. The key identifies the question and its answer, so it
   * must be the same everywhere the question is built, also across restarts and services.
   */
  public Question.Predicate build(String key) {
    return new Question.Predicate(key, instructions, whenTrue, whenFalse);
  }
}
