/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.docreview;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.docreview.api.DocReviewEndpoint;
import demo.docreview.application.DocumentReviewer;
import demo.docreview.application.ReviewResult;
import demo.docreview.application.ReviewTasks;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class DocReviewIntegrationTest extends TestKitSupport {

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    ).withModelProvider(DocumentReviewer.class, model);
  }

  @Test
  public void shouldReviewAttachedDocument() {
    model.fixedResponse(
      new TestModelProvider.AiResponse(
        completeTask(
          new ReviewResult(
            "Compliant",
            List.of("All sections present", "Proper signatures"),
            true
          )
        )
      )
    );

    var response = httpClient
      .POST("/docreview")
      .withRequestBody(
        new DocReviewEndpoint.CreateReview(
          "SERVICES AGREEMENT\n1. PARTIES: Acme Corp and Client Inc\n" +
          "2. TERMS: 12 months\n3. SIGNATURES: [signed]",
          "Check for SOX compliance"
        )
      )
      .responseBodyAs(DocReviewEndpoint.ReviewResponse.class)
      .invoke()
      .body();

    var taskId = response.id();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(taskId).get(ReviewTasks.REVIEW);
        var result = snapshot.result().orElseThrow();
        assertThat(result.compliant()).isTrue();
        assertThat(result.assessment()).isEqualTo("Compliant");
        assertThat(result.findings()).contains("All sections present", "Proper signatures");
      });
  }
}
