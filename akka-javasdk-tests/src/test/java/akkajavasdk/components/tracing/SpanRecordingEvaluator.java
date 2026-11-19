/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracing;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import io.opentelemetry.api.trace.Span;

/** Evaluator that records its current span and starts a custom span. */
@Component(id = "span-recording-evaluator")
public class SpanRecordingEvaluator extends Evaluator {

  public static final String CUSTOM_SPAN = "span-recording-evaluator custom";

  @Override
  public Effect evaluate(EvaluationContext context) {
    SpanRecorder.record("evaluator:" + context.attributes().get("key"));
    context.tracing().startSpan(CUSTOM_SPAN).ifPresent(Span::end);
    return effects().complete(Evaluation.passed("recorded"));
  }
}
