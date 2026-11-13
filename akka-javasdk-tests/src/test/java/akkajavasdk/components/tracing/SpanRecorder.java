/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Records the span that is current where a test component calls it. */
public class SpanRecorder {

  private static final Map<String, SpanContext> RECORDED = new ConcurrentHashMap<>();

  public static void record(String key) {
    RECORDED.put(key, Span.current().getSpanContext());
  }

  public static SpanContext recorded(String key) {
    return RECORDED.get(key);
  }
}
