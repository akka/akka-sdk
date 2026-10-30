/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;

/** An evaluator that the component descriptor of this module does not list. */
@Component(id = "unregistered-evaluator")
public class UnregisteredEvaluator extends Evaluator {

  @Override
  public Effect evaluate(EvaluationContext context) {
    return effects().notApplicable("never started");
  }
}
