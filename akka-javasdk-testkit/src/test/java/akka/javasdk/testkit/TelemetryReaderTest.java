/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelemetryReaderTest {

  @Test
  void aFunctionToolLosesItsClassPrefix() {
    assertThat(
            TelemetryReader.toolName("SupportAgent_getCustomer", "com.example.SupportAgent", false))
        .isEqualTo("getCustomer");
    assertThat(TelemetryReader.toolName("WeatherTools_forecast", "com.example.SupportAgent", false))
        .isEqualTo("forecast");
    assertThat(TelemetryReader.toolName("SupportAgent_getCustomer", null, false))
        .isEqualTo("getCustomer");
    assertThat(TelemetryReader.toolName("get_customer", null, false)).isEqualTo("get_customer");
  }

  @Test
  void anMcpToolKeepsTheNameItsServerGaveIt() {
    assertThat(TelemetryReader.toolName("Github_createIssue", "com.example.SupportAgent", true))
        .isEqualTo("Github_createIssue");
    assertThat(TelemetryReader.toolName("Github_createIssue", null, true))
        .isEqualTo("Github_createIssue");
  }
}
