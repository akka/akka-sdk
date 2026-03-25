/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.pipeline.api.PipelineEndpoint;
import demo.pipeline.application.PipelineTasks;
import demo.pipeline.application.ReportAgent;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class PipelineIntegrationTest extends TestKitSupport {

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    ).withModelProvider(ReportAgent.class, model);
  }

  @Test
  public void shouldProcessPipelineInDependencyOrder() {
    // The model always responds with complete_task. This tests that the pipeline
    // orchestration (dependency ordering, task completion) works correctly.
    // The session accumulates across tasks, so we use a universal response.
    model.fixedResponse(
      new TestModelProvider.AiResponse(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"phase\":\"done\",\"content\":\"task completed\"}"
        )
      )
    );

    var response = httpClient
      .POST("/pipeline")
      .withRequestBody(new PipelineEndpoint.CreatePipeline("test topic"))
      .responseBodyAs(PipelineEndpoint.PipelineResponse.class)
      .invoke()
      .body();

    assertThat(response.collectTaskId()).isNotBlank();
    assertThat(response.analyzeTaskId()).isNotBlank();
    assertThat(response.reportTaskId()).isNotBlank();

    // Wait for the final report task to complete (it depends on analyze, which depends on collect)
    Awaitility.await()
      .ignoreExceptions()
      .atMost(30, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var reportSnapshot = componentClient
          .forTask(response.reportTaskId())
          .get(PipelineTasks.REPORT);
        assertThat(reportSnapshot.status().name()).isEqualTo("COMPLETED");
        assertThat(reportSnapshot.result()).isNotNull();
      });

    // Verify all phases completed in dependency order
    var collectSnapshot = componentClient
      .forTask(response.collectTaskId())
      .get(PipelineTasks.COLLECT);
    assertThat(collectSnapshot.status().name()).isEqualTo("COMPLETED");

    var analyzeSnapshot = componentClient
      .forTask(response.analyzeTaskId())
      .get(PipelineTasks.ANALYZE);
    assertThat(analyzeSnapshot.status().name()).isEqualTo("COMPLETED");
  }
}
