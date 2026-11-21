/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import com.typesafe.config.Config;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;

/**
 * INTERNAL API
 *
 * <p>The settings of the A2A endpoint, read from {@code akka.javasdk.a2a.endpoint} when the service
 * starts.
 *
 * @param publicUrl the public base URL of the service, empty when not set
 * @param blockingWait how long a blocking {@code SendMessage} on a long skill waits for the task to
 *     end or ask for input
 * @param streamIdleTimeout how long a streaming agent may send no token before its task fails
 * @param signer the key that signs the Agent Card, empty when the card is not signed
 * @param pushUrls which push notification URLs the endpoint calls
 */
@InternalApi
public record A2aEndpointSettings(
    String publicUrl,
    Duration blockingWait,
    Duration streamIdleTimeout,
    Optional<AgentCardSigning.Signer> signer,
    A2aPushUrlPolicy pushUrls) {

  /**
   * @param endpoint the {@code akka.javasdk.a2a.endpoint} section
   * @param devMode true when the service runs in dev mode
   * @throws IllegalArgumentException when a setting is invalid
   */
  public static A2aEndpointSettings fromConfig(Config endpoint, boolean devMode) {
    var blockingWait = endpoint.getDuration("blocking-wait");
    if (blockingWait.isNegative()) {
      throw new IllegalArgumentException(
          "akka.javasdk.a2a.endpoint.blocking-wait must not be negative");
    }
    var streamIdleTimeout = endpoint.getDuration("stream-idle-timeout");
    if (streamIdleTimeout.isNegative() || streamIdleTimeout.isZero()) {
      throw new IllegalArgumentException(
          "akka.javasdk.a2a.endpoint.stream-idle-timeout must be positive");
    }
    return new A2aEndpointSettings(
        endpoint.getString("public-url"),
        blockingWait,
        streamIdleTimeout,
        AgentCardSigning.Signer.fromConfig(endpoint.getConfig("signing")),
        new A2aPushUrlPolicy(devMode, new HashSet<>(endpoint.getStringList("push.allowed-hosts"))));
  }
}
