/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.Metadata;

public interface DeferredCallDetails<I, O> {
  /** @return The forwarded message */
  I getMessage();

  /** @return Any metadata attached to the call */
  Metadata getMetadata();

  /** @return The name of the service being called */
  String getServiceName();

  /** @return The method name being called */
  String getMethodName();
}
