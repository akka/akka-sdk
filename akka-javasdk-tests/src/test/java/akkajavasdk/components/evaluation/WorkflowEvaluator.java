/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import java.util.concurrent.CompletionStage;

/**
 * An evaluator that delegates a multi-step evaluation to a durable workflow, keyed by the
 * evaluation id, and suspends via {@code asyncEffect} until the workflow publishes its completion
 * notification.
 */
public class WorkflowEvaluator extends Evaluator {

  private final ComponentClient componentClient;
  private final Materializer materializer;

  public WorkflowEvaluator(ComponentClient componentClient, Materializer materializer) {
    this.componentClient = componentClient;
    this.materializer = materializer;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    String transcript = "interaction-" + context.subject().sequenceNr();
    String workflowId = context.evaluationId();

    // Subscribe to the workflow's notifications before starting it, so the completion report can't
    // be missed, and take the first one instead of polling for state.
    CompletionStage<EvaluationWorkflow.Report> reportStage =
        componentClient
            .forWorkflow(workflowId)
            .notificationStream(EvaluationWorkflow::updates)
            .source()
            .runWith(Sink.head(), materializer);

    CompletionStage<Effect> futureEffect =
        componentClient
            .forWorkflow(workflowId)
            .method(EvaluationWorkflow::run)
            .invokeAsync(transcript)
            .thenCompose(started -> reportStage)
            .thenApply(
                report ->
                    effects().record(Evaluation.passed(report.reason()).withScore(report.score())));

    return effects().asyncEffect(futureEffect);
  }
}
