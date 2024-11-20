/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.annotation.DoNotInherit;
import io.opentelemetry.api.trace.Span;

import java.util.Optional;

/**
 * Factory for manually creating open telemetry spans in addition to those automatically provided by
 * the runtime and SDK.
 *
 * <p>Not for user extension. Injectable into endpoint constructors or available through component command contexts.
 */
@DoNotInherit
public interface Tracing {
  /**
   * If tracing is enabled, create and start a new custom span with the given name, setting a parent
   * for the span is done automatically so that the span is a child of the incoming request or
   * component call.
   *
   * @return Optional of the span if tracing is enabled, empty option if tracing is not enabled.
   */
  Optional<Span> startSpan(String name);

  /**
   * If tracing is enabled, this returns the current parent span, to use for propagating trace parent
   * through third party integrations.
   */
  Optional<Span> parentSpan();
}
