/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the version of an {@code Evaluator} or {@code WorkflowEvaluator}.
 *
 * <p>A change to what an evaluator measures or how it measures it — a different prompt, a different
 * scoring rule, a different model — is a new version, not a silent change under the same one: a
 * comparison between two runs is only meaningful when the evaluator that judged them stayed the
 * same, or when the two versions are compared deliberately.
 *
 * <p>Defaults to {@code "1"} when absent.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EvaluatorVersion {
  /** The version of this evaluator. */
  String value() default "1";
}
