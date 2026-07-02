package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;

@Component(id = "evaluator-without-binding")
public class EvaluatorWithoutBinding extends Evaluator {

  public Effect evaluate(EvaluationContext context) {
    return effects().inconclusive("not implemented");
  }
}
