package com.example.evaluator;

import akka.javasdk.evaluation.Evaluation;

public class EvaluationBuilding {

  Evaluation example() {
    // tag::build[]
    return Evaluation.passed("Response was accurate and helpful")
      .withScore(0.92)
      .withLabel("excellent")
      .withAttribute("model", "gpt-4o");
    // end::build[]
  }
}
