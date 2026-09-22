package com.example.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// tag::judgment-test[]
import akka.javasdk.agent.Judgment;
import akka.javasdk.testkit.TestJudgmentModelProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class TriageAgentIntegrationTest extends TestKitSupport {

  private final TestJudgmentModelProvider judgmentProvider = new TestJudgmentModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withJudgmentModelProvider(TriageAgent.class, judgmentProvider); // <1>
  }

  @Test
  public void routesBillingTickets() {
    judgmentProvider
      .whenState("My payouts have been failing for 3 days.") // <2>
      .reply(
        Map.of(
          "route", TestJudgmentModelProvider.choice("billing"), // <3>
          "severity", TestJudgmentModelProvider.score(2.0),
          "urgent", TestJudgmentModelProvider.yes()
        )
      );

    Judgment judgment = componentClient
      .forAgent()
      .inSession(UUID.randomUUID().toString())
      .method(TriageAgent::triage)
      .invoke("My payouts have been failing for 3 days.");

    assertThat(judgment.choice("route").choice()).isEqualTo("billing");
    assertThat(judgment.yesNo("urgent").isYes()).isTrue();
  }

  @Test
  public void failsWhenNoAnswerIsConfigured() {
    assertThatThrownBy(() -> // <4>
      componentClient
        .forAgent()
        .inSession(UUID.randomUUID().toString())
        .method(TriageAgent::triage)
        .invoke("Unknown ticket")
    ).hasMessageContaining("No judgment configured");
  }
}
// end::judgment-test[]
