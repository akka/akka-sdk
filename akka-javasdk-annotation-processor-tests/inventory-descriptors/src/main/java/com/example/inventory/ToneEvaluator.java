/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;

@Component(id = "tone")
public class ToneEvaluator extends Evaluator {

  @Override
  public Effect evaluate(EvaluationContext context) {
    return effects().notApplicable("nothing to evaluate");
  }
}
