/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
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
   * configured to connect to another service deployed with that name on the same Akka project. For
   * a service that is part of a platform service, it connects to a service with that name in the
   * same platform service. The runtime will take care of routing requests to the service and
   * keeping the data safe by encrypting the connection between services and identifying the client
   * as coming from this service.
   *
   * <p>If it is a full dns name prefixed with "http://" or "https://" it will connect to services
   * available on the public internet.
   */
  HttpClient httpClientFor(String serviceName);

  /**
   * Returns a provider for HTTP clients to services that belong to a platform service enabled in
   * this project. A service name passed to {@link #httpClientFor(String)} on the returned provider
   * is resolved among the services of the platform service instead of the services of this project.
   * The runtime routes and encrypts the calls the same way as calls to services in this project.
   *
   * <p>The returned provider only accepts a bare service name, and throws {@link
   * IllegalArgumentException} for a host name or a URL.
   *
   * <p>The platform service identifies the caller as a service of the project that the platform
   * service is enabled in. Only {@code @Acl.Matcher} rules with scope {@link
   * akka.javasdk.annotations.Acl.Scope#PARENT_PROJECT} in the platform service match such a caller.
   * The platform service ignores the identity of the calling component.
   *
   * <p>In dev mode and in the test kit, the service is looked up by name only, the same way as a
   * service in this project. A mock registered in the test kit for a service name also applies to a
   * service of that name in a platform service.
   *
   * <p>Calling a service in a platform service fails when the deployment has no project id, for
   * example in a standalone deployment. A service that is part of a platform service can only call
   * services in its own platform service. Passing the own platform service resolves names the same
   * way as this provider.
   *
   * @param platformService the name of the platform service, for example {@code
   *     my-platform-service}
   * @throws IllegalArgumentException if the name is not a lowercase DNS label of at most 26
   *     characters, if the name is the reserved name {@code svc}, or if this service is part of
   *     another platform service
   */
  HttpClientProvider forPlatformService(String platformService);
}
