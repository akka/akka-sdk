package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.EvaluatesAgent;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;

@Component(id = "")
@EvaluatesAgent(componentId = "some-agent")
public class EvaluatorWithEmptyComponentId extends Evaluator {

  public Effect evaluate(EvaluationContext context) {
    return effects().inconclusive("not implemented");
  }
}
