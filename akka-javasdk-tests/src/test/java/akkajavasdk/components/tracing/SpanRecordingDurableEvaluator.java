/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracing;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.DurableEvaluator;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import io.opentelemetry.api.trace.Span;

/**
 * Durable evaluator that records its current span and starts a custom span, at the start and in its
 * step.
 */
@Component(id = "span-recording-durable-evaluator")
public class SpanRecordingDurableEvaluator
    extends DurableEvaluator<SpanRecordingDurableEvaluator.State> {

  public static final String CUSTOM_START_SPAN = "span-recording-durable-evaluator custom start";
  public static final String CUSTOM_STEP_SPAN = "span-recording-durable-evaluator custom step";

  public record State() {}

  @Override
  public Effect onEvaluation(EvaluationContext context) {
    SpanRecorder.record("durable-evaluator-start:" + context.attributes().get("key"));
    context.tracing().startSpan(CUSTOM_START_SPAN).ifPresent(Span::end);
    return effects().transitionTo(SpanRecordingDurableEvaluator::recordStep);
  }

  private Effect recordStep() {
    var context = evaluationContext();
    SpanRecorder.record("durable-evaluator-step:" + context.attributes().get("key"));
    context.tracing().startSpan(CUSTOM_STEP_SPAN).ifPresent(Span::end);
    return effects().complete(Evaluation.passed("recorded"));
  }
}
