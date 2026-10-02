/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.judgment;

import static akkajavasdk.components.judgment.TriageEndpoint.ROUTE;
import static akkajavasdk.components.judgment.TriageEndpoint.SEVERITY;
import static akkajavasdk.components.judgment.TriageEndpoint.URGENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.testkit.TestJudgmentModelProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.Junit5LogCapturing;
import akkajavasdk.components.judgment.TriageEndpoint.Ticket;
import akkajavasdk.components.judgment.TriageEndpoint.TriageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class JudgmentClientIntegrationTest extends TestKitSupport {

  private final TestJudgmentModelProvider testJudgmentModel = new TestJudgmentModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withJudgmentModelProvider(testJudgmentModel);
  }

  @AfterEach
  public void afterEach() {
    testJudgmentModel.reset();
  }

  @Test
  public void answerQuestionsThroughTheInjectedClient() {
    var ticket = new Ticket("Site down", "The checkout page fails for every customer");
    testJudgmentModel
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
        .isEqualTo(new TriageResult("technical", 2.0, 0.9, TestJudgmentModelProvider.MODEL_NAME));
  }

  @Test
  public void failTheCallWhenTheModelGivesNoAnswer() {
    testJudgmentModel.whenState(new Ticket("other", "other")).yesNo(URGENT, 0.1);

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
