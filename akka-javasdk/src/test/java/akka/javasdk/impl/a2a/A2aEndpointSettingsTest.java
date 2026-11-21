/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class A2aEndpointSettingsTest {

  private static Config endpoint(String hocon) {
    return ConfigFactory.parseString(hocon)
        .withFallback(ConfigFactory.load())
        .getConfig("akka.javasdk.a2a.endpoint");
  }

  @Test
  void readsTheDefaults() {
    var settings = A2aEndpointSettings.fromConfig(endpoint(""), false);
    assertEquals(Duration.ofSeconds(10), settings.blockingWait());
    assertTrue(settings.signer().isEmpty());
    assertFalse(settings.pushUrls().devMode());
    assertEquals(Set.of(), settings.pushUrls().allowedHosts());
  }

  @Test
  void readsTheBlockingWaitAndTheAllowedPushHosts() {
    var settings =
        A2aEndpointSettings.fromConfig(
            endpoint(
                """
                akka.javasdk.a2a.endpoint {
                  blocking-wait = 2s
                  push-delivery.allowed-hosts = ["Hooks.Internal"]
                }
                """),
            true);
    assertEquals(Duration.ofSeconds(2), settings.blockingWait());
    assertTrue(settings.pushUrls().devMode());
    assertEquals(Set.of("hooks.internal"), settings.pushUrls().allowedHosts());
  }

  @Test
  void refusesASigningKeyFileThatCannotBeRead() {
    var e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                A2aEndpointSettings.fromConfig(
                    endpoint(
                        """
                        akka.javasdk.a2a.endpoint.signing {
                          key-file = "/no/such/key.pem"
                          key-id = "k1"
                        }
                        """),
                    false));
    assertTrue(e.getMessage().contains("key-file"));
  }
}
