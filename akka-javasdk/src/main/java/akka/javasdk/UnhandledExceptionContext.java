/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.javasdk.annotations.ComponentId;
import java.util.Optional;

/**
 * Information about an exception thrown by user code that the runtime caught and turned into a
 * generic 500 response with a correlation id.
 *
 * <p>Passed to {@link UnhandledExceptionHandler#onUnhandledException(UnhandledExceptionContext)}.
 */
public interface UnhandledExceptionContext {

  /** The original throwable thrown by user code, with its full stack trace and causes. */
  Throwable throwable();

  /**
   * The correlation id surfaced to the client and present in the runtime log MDC at the time the
   * exception was caught. Useful for correlating the error tracker event with runtime logs.
   */
  String correlationId();

  /**
   * The entity, workflow or agent id when the exception originated in a stateful component. Empty
   * for endpoints and other stateless components.
   */
  Optional<String> subjectId();

  /**
   * Identifier for the component the exception originated in. For components declaring a {@link
   * ComponentId}, this is the annotation value. For endpoints (HTTP, gRPC, MCP) which have no
   * {@code @ComponentId}, this falls back to the simple class name.
   */
  String componentId();

  /** Fully-qualified class name of the user component the exception originated in. */
  String componentClassName();
}
