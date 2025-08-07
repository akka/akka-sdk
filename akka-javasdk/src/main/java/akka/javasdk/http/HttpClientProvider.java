/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

import akka.annotation.DoNotInherit;

/**
 * Provider for HTTP clients to interact with other services over HTTP.
 *
 * <p>HttpClientProvider enables HTTP endpoints and other components to make outbound HTTP calls to
 * other services, both within the same Akka project and to external services on the internet.
 *
 * <p><strong>Service-to-Service Communication:</strong> When calling other Akka services deployed
 * in the same project, use the service name without protocol or domain. The runtime handles
 * routing, encryption, and authentication automatically.
 *
 * <p><strong>External Service Communication:</strong> For external services, provide full URLs with
 * {@code http://} or {@code https://} protocols. These calls go over the public internet and
 * require appropriate authentication.
 *
 * <p><strong>Usage in Endpoints:</strong> Inject HttpClientProvider into endpoint constructors to
 * access HTTP client functionality. The provider creates configured HTTP clients for specific
 * services or URLs.
 *
 * <p><strong>Security:</strong> Service-to-service calls within the same project are automatically
 * secured with mutual TLS and service identity verification. External calls require manual
 * authentication setup.
 *
 * <p>Not for user extension, instances provided by the SDK through dependency injection.
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
