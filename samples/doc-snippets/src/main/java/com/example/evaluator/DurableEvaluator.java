package com.example.evaluator;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import java.util.concurrent.CompletionStage;

/**
 * An evaluator that delegates a multi-step evaluation to a durable workflow and suspends via
 * {@code asyncEffect} until the workflow publishes its completion report.
 */
@Component(id = "durable-evaluator")
public class DurableEvaluator extends Evaluator {

  private final ComponentClient componentClient;
  private final Materializer materializer;

  public DurableEvaluator(ComponentClient componentClient, Materializer materializer) {
    this.componentClient = componentClient;
    this.materializer = materializer;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    String transcript = context.subject().interactionId();
    String workflowId = context.evaluationId();
    // tag::async[]
    // Subscribe to the workflow's completion report before starting it, so it can't be missed.
    CompletionStage<EvaluationWorkflow.Report> reportStage = componentClient
        .forWorkflow(workflowId)
        .notificationStream(EvaluationWorkflow::updates)
        .source()
        .runWith(Sink.head(), materializer);

    CompletionStage<Effect> futureEffect = componentClient
        .forWorkflow(workflowId)
        .method(EvaluationWorkflow::run)
        .invokeAsync(transcript)
        .thenCompose(started -> reportStage)
        .thenApply(report ->
            effects().complete(Evaluation.passed(report.reason()).withScore(report.score())));

    return effects().asyncEffect(futureEffect);
    // end::async[]
  }
}
