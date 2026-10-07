/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import akka.javasdk.testkit.TestKit;
import akkajavasdk.Junit5LogCapturing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class StreamingAgentGuardrailStartupTest {

  @Test
  public void shouldFailStartupForBlockingAgentResponseGuardrailOnStreamingAgent() {
    TestKit testKit =
        new TestKit(
            TestKit.Settings.DEFAULT
                .withEphemeralPort()
                .withAdditionalConfig(
                    """
                    akka.javasdk.agent.guardrails {
                      "streaming response guard" {
                        class = "akkajavasdk.components.agent.BlockingResponseGuard"
                        agents = ["some-streaming-agent"]
                        category = TOXIC
                        block-reason = "blocked"
                      }
                    }
                    """));

    try {
      Throwable thrown = catchThrowable(testKit::start);

      assertThat(thrown)
          .rootCause()
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Agent [some-streaming-agent]")
          .hasMessageContaining("Guardrail [streaming response guard]");
    } finally {
      testKit.stop();
    }
  }

  @Test
  public void shouldStartForBlockingLegacyModelResponseGuardrailOnStreamingAgent() {
    TestKit testKit =
        new TestKit(
            TestKit.Settings.DEFAULT
                .withEphemeralPort()
                .withAdditionalConfig(
                    """
                    akka.javasdk.agent.guardrails {
                      "streaming legacy guard" {
                        class = "akkajavasdk.components.agent.TestGuardrail"
                        agents = ["some-streaming-agent"]
                        category = TOXIC
                        use-for = ["model-response"]
                        search-for = "bad stuff"
                      }
                    }
                    """));

    try {
      testKit.start();
    } finally {
      testKit.stop();
    }
  }
}
