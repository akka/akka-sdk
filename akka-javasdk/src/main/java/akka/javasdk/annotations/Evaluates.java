/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import akka.javasdk.evaluation.Subject;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the {@link Subject} kinds an {@code Evaluator} or {@code WorkflowEvaluator} evaluates.
 *
 * <p>A binding under {@code akka.javasdk.evaluation.evaluators} that would fire the evaluator on a
 * subject kind it does not declare here is rejected at startup, rather than reaching the evaluator
 * as an interaction it cannot make sense of.
 *
 * <p>Defaults to {@link Subject.Interaction} when absent, since a binding under {@code agents} (the
 * only binding kind today) always fires on an interaction.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Evaluates {
  /** The subject kinds this evaluator can be bound to. */
  Class<? extends Subject>[] value();
}
