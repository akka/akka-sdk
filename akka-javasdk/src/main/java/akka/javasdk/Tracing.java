/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.annotation.DoNotInherit;
import io.opentelemetry.api.trace.Span;

import java.util.Optional;

/**
 * Factory for manually creating open telemetry spans in addition to those automatically provided by
 * the runtime and SDK.
 *
 * <p>Not for user extension. Injectable into endpoint constructors or available through component
 * command contexts.
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
   * If tracing is enabled, this returns the current parent span, to use for propagating trace
   * parent through third party integrations. This span should only be used for observing, ending it
   * or marking it as failed etc. is managed by the SDK and the runtime.
   *
   * @see {{@link #startSpan(String)}} for creating a custom span tied to some logic in a service.
   */
  Optional<Span> parentSpan();
}
