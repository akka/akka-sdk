/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds an {@link akka.javasdk.evaluation.Evaluator} to an agent, so that the evaluator is invoked
 * for each interaction of that agent.
 *
 * <p>Repeatable: annotate an evaluator with one {@code @EvaluatesAgent} per agent it evaluates.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(EvaluatesAgents.class)
public @interface EvaluatesAgent {

  /**
   * The component id of the agent to evaluate.
   *
   * @return the agent component id
   */
  String componentId();
}
