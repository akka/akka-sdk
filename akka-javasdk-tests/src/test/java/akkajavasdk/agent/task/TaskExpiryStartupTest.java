/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.agent.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import akka.javasdk.testkit.TestKit;
import akkajavasdk.Junit5LogCapturing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class TaskExpiryStartupTest {

  @Test
  public void shouldFailStartupForInvalidDefaultExpiry() {
    TestKit testKit =
        new TestKit(
            TestKit.Settings.DEFAULT
                .withEphemeralPort()
                .withAdditionalConfig("akka.javasdk.agent.task.expire-after = foo"));

    try {
      Throwable thrown = catchThrowable(testKit::start);

      assertThat(thrown)
          .rootCause()
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("[akka.javasdk.agent.task.expire-after]");
    } finally {
      testKit.stop();
    }
  }
}
