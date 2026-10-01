/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The label used in the report to group an evaluator's results. Required on every {@link Evaluator}
 * class. A case refuses an evaluator whose class does not carry it.
 *
 * <p>For a custom evaluator, the report adds {@link Evaluators#CUSTOM_PREFIX} to the label. The
 * label must not start with that prefix.
 *
 * <p>Labels must be unique across all evaluators in an experiment. The rule is per class, not per
 * instance.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EvalLabel {

  /** The label. Not blank. */
  String value();
}
