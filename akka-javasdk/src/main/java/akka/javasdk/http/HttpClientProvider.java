/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

public interface HttpClientProvider {

  /**
   * Returns a {@link HttpClient} configured to connect to another service deployed on the same
   * project.
   *
   * <p>The service is identified only by the name it has been deployed. The runtime takes care of
   * routing requests to the service and keeping the data safe by encrypting the connection.
   */
  HttpClient httpClientFor(String serviceName);
}
