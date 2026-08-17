/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import java.util.Optional;

/**
 * Context passed to an {@link Evaluator} when an evaluation is triggered.
 *
 * <p>Exposes the {@link Subject} being evaluated and the id of this evaluation.
 *
 * <p>Not for user extension.
 */
public interface EvaluationContext {

  /**
   * The subject being evaluated.
   *
   * @return the subject of this evaluation
   */
  Subject subject();

  /**
   * The unique id of this evaluation.
   *
   * @return the evaluation id
   */
  String evaluationId();

  /**
   * The content of the interaction being evaluated, present only when {@link #subject()} is a
   * {@link Subject.Interaction}.
   *
   * <p>Reading it here needs no client call: the runtime resolves the interaction before the
   * evaluator runs. A {@link Subject.Flow} or {@link Subject.Session} names a collection too large
   * to resolve eagerly and is read instead through an injected {@code SubjectClient}.
   *
   * @return the interaction's content, or empty for any other subject kind
   */
  Optional<Interaction> interaction();
}
