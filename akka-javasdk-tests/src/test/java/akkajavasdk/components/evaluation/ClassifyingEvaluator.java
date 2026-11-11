/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.agent.ClassifierClient;
import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;

/**
 * An evaluator that classifies the trigger attribute {@code text} with the classifier {@code
 * toxicity-test-classifier}. It is bound to no agent. The verdict carries the id of the
 * classification record in the attribute {@code classificationId}.
 */
@Component(id = "classifying-evaluator")
public class ClassifyingEvaluator extends Evaluator {

  private final ClassifierClient classifierClient;

  public ClassifyingEvaluator(ClassifierClient classifierClient) {
    this.classifierClient = classifierClient;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    var result =
        classifierClient.classifyWithResult(
            "toxicity-test-classifier", context.attributes().get("text"));
    var label = result.classification().label().orElse("no label");
    return effects()
        .complete(
            Evaluation.passed("classified as " + label)
                .withAttribute("classificationId", result.classificationId().orElse("")));
  }
}
