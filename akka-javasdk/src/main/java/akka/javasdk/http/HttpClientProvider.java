/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

import akka.annotation.DoNotInherit;

/**
 * Not for user extension, instances are available through injection in selected component types.
 */
@DoNotInherit
public interface HttpClientProvider {

  /**
   * Returns a {@link HttpClient} to interact with the specified HTTP service.
   *
   * <p>If the serviceName is a service name without protocol or domain the client will be
   * configured to connect to another service deployed with that name on the same Akka project. The
   * runtime will take care of routing requests to the service and keeping the data safe by
   * encrypting the connection between services and identifying the client as coming from this
   * service.
   *
   * <p>If it is a full dns name prefixed with "http://" or "https://" it will connect to services
   * available on the public internet.
   */
  HttpClient httpClientFor(String serviceName);
}
