/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracecontext;

import akka.javasdk.Tracing;
import io.opentelemetry.api.trace.Span;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The span ids that a handler sees: the parent span of the {@link Tracing}, the current span of the
 * handler thread, and the current span in a task on the injected executor.
 */
public record SpanIds(String parent, String current, String inTask) {

  public static final String NONE = "none";

  public static SpanIds observe(Tracing tracing) {
    return new SpanIds(parentSpanId(tracing), currentSpanId(), NONE);
  }

  public static SpanIds observe(Tracing tracing, Executor executor) {
    var inTask = CompletableFuture.supplyAsync(SpanIds::currentSpanId, executor).join();
    return new SpanIds(parentSpanId(tracing), currentSpanId(), inTask);
  }

  private static String parentSpanId(Tracing tracing) {
    return tracing.parentSpan().map(span -> span.getSpanContext().getSpanId()).orElse(NONE);
  }

  private static String currentSpanId() {
    var spanContext = Span.current().getSpanContext();
    return spanContext.isValid() ? spanContext.getSpanId() : NONE;
  }
}
