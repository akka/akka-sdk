/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import com.typesafe.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class LedgerRecordsTestKitTest {

  @Test
  public void shouldPassTheOptionalLedgerRecordsToTheRuntime() {
    TestKit testKit =
        new TestKit(
            TestKit.Settings.DEFAULT
                .withEphemeralPort()
                .withAdditionalConfig(
                    """
                    akka.javasdk.agent.ledger.enabled = false
                    akka.javasdk.agent.ledger.records {
                      tool-call-intent = false
                      sanitizer-no-masking = false
                    }
                    """));

    try {
      testKit.start();
      Config runtimeConfig = testKit.getActorSystem().settings().config();

      // the testkit runs in dev mode, which enables the ledger but keeps the records as configured
      assertThat(runtimeConfig.getBoolean("akka.runtime.ledger.enabled")).isTrue();
      assertThat(runtimeConfig.getBoolean("akka.runtime.ledger.records.tool-call-intent"))
          .isFalse();
      assertThat(runtimeConfig.getBoolean("akka.runtime.ledger.records.guardrail-allow")).isTrue();
      assertThat(runtimeConfig.getBoolean("akka.runtime.ledger.records.sanitizer-no-masking"))
          .isFalse();
    } finally {
      testKit.stop();
    }
  }
}
