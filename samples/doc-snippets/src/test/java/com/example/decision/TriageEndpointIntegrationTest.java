package com.example.decision;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestDecisionModelProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

public class TriageEndpointIntegrationTest extends TestKitSupport {

  // tag::settings[]
  private final TestDecisionModelProvider testDecisionModel = new TestDecisionModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withDecisionModelProvider(testDecisionModel);
  }

  // end::settings[]

  @Test
  public void routesTheTicket() {
    var ticket = new TriageEndpoint.Ticket("Refund", "Charged twice for my subscription");

    // tag::rules[]
    testDecisionModel
      .whenInput(ticket)
      .choice(TriageEndpoint.ROUTE, "billing")
      .score(TriageEndpoint.SEVERITY, 2)
      .predicate(TriageEndpoint.URGENT, 0.9);
    // end::rules[]

    var triage = httpClient
      .POST("/triage")
      .withRequestBody(ticket)
      .responseBodyAs(TriageEndpoint.Triage.class)
      .invoke()
      .body();

    assertThat(triage.team()).isEqualTo("billing");
    assertThat(triage.severity()).isEqualTo(2.0);
    assertThat(triage.urgent()).isTrue();
    assertThat(triage.model()).isEqualTo(TestDecisionModelProvider.MODEL_NAME);
  }
}
