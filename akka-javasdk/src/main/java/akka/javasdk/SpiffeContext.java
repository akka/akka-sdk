/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.annotation.DoNotInherit;
import java.util.Optional;

/**
 * SPIFFE identity information available during request processing. Provides access to the
 * component's own identity and, for HTTP/gRPC endpoints, the optional identity of the calling
 * component.
 *
 * <p>Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface SpiffeContext {

  /**
   * The SPIFFE ID assigned to this component, e.g. {@code
   * spiffe://trust/svc/service/http-endpoint/MyEndpoint}.
   */
  String getSpiffeId();

  /**
   * The SPIFFE identity of the calling component, if the caller included the {@code
   * _kalix-caller-spiffe} header. Present only when an Akka service in the same project called this
   * endpoint. Empty for external/internet callers.
   */
  Optional<CallerSpiffeContext> getCallerContext();
}
