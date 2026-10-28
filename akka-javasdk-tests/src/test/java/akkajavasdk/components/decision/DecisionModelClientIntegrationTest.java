/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.decision;

import static akkajavasdk.components.decision.TriageEndpoint.ROUTE;
import static akkajavasdk.components.decision.TriageEndpoint.SEVERITY;
import static akkajavasdk.components.decision.TriageEndpoint.URGENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.testkit.TestDecisionModelProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.Junit5LogCapturing;
import akkajavasdk.components.decision.TriageEndpoint.Ticket;
import akkajavasdk.components.decision.TriageEndpoint.TriageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class DecisionModelClientIntegrationTest extends TestKitSupport {

  private final TestDecisionModelProvider testDecisionModel = new TestDecisionModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withDecisionModelProvider(testDecisionModel);
  }

  @AfterEach
  public void afterEach() {
    testDecisionModel.reset();
  }

  @Test
  public void answerQuestionsThroughTheInjectedClient() {
    var ticket = new Ticket("Site down", "The checkout page fails for every customer");
    testDecisionModel
        .whenState(ticket)
        .choice(ROUTE, "technical")
        .score(SEVERITY, 2)
        .yesNo(URGENT, 0.9);

    var result =
        httpClient
            .POST("/triage")
            .withRequestBody(ticket)
            .responseBodyAs(TriageResult.class)
            .invoke()
            .body();

    assertThat(result)
        .isEqualTo(new TriageResult("technical", 2.0, 0.9, TestDecisionModelProvider.MODEL_NAME));
  }

  @Test
  public void failTheCallWhenTheModelGivesNoAnswer() {
    testDecisionModel.whenState(new Ticket("other", "other")).yesNo(URGENT, 0.1);

    assertThatThrownBy(
            () ->
                httpClient
                    .POST("/triage")
                    .withRequestBody(new Ticket("Refund", "Charged twice"))
                    .responseBodyAs(TriageResult.class)
                    .invoke())
        .isInstanceOf(RuntimeException.class);
  }
}
