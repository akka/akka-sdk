/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akkajavasdk.components.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class ServiceSetupLifecycleTest {

  @Test
  public void onShutdownIsInvokedWhenTestKitStops() {
    Bootstrap.onShutdownCalled.set(false);

    TestKit testKit = new TestKit().start();
    try {
      assertThat(Bootstrap.onShutdownCalled).isFalse();
    } finally {
      testKit.stop();
    }

    assertThat(Bootstrap.onShutdownCalled).isTrue();
  }
}
