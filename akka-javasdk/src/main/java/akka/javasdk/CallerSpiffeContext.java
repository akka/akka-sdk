/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.annotation.DoNotInherit;

/**
 * The SPIFFE identity of the calling component, propagated via the {@code _kalix-caller-spiffe}
 * internal header. Provides programmatic access to the caller's identity for HTTP and gRPC
 * endpoints. Attestation of the service-level identity is provided by the service mesh (Linkerd).
 *
 * <p>Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface CallerSpiffeContext {

  /**
   * The full SPIFFE ID of the calling component, e.g. {@code
   * spiffe://trust/svc/service/agent/MyAgent}.
   */
  String getSpiffeId();
}
