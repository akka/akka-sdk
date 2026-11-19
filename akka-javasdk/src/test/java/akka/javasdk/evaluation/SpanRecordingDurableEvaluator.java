/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import akka.javasdk.annotations.Component;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable evaluator that records, at the start and in its step, the current span and the parent
 * span of its tracing.
 */
@Component(id = "span-recording-durable-evaluator")
public class SpanRecordingDurableEvaluator
    extends DurableEvaluator<SpanRecordingDurableEvaluator.State> {

  public record State() {}

  public record Recorded(SpanContext current, Optional<SpanContext> tracingParent) {}

  private static final Map<String, Recorded> RECORDED = new ConcurrentHashMap<>();

  public static Recorded recorded(String key) {
    return RECORDED.get(key);
  }

  private static void record(String key, EvaluationContext context) {
    RECORDED.put(
        key,
        new Recorded(
            Span.current().getSpanContext(),
            context.tracing().parentSpan().map(Span::getSpanContext)));
  }

  @Override
  public Effect onEvaluation(EvaluationContext context) {
    record("start:" + context.evaluationId(), context);
    return effects().transitionTo(SpanRecordingDurableEvaluator::recordStep);
  }

  private Effect recordStep() {
    record("step:" + evaluationContext().evaluationId(), evaluationContext());
    return effects().complete(Evaluation.passed("recorded"));
  }
}
