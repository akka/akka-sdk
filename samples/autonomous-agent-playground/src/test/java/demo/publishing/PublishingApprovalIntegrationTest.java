package demo.publishing;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.publishing.api.PublishingEndpoint;
import demo.publishing.application.ContentAgent;
import demo.publishing.application.PublishingAgent;
import demo.publishing.application.PublishingTasks;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class PublishingApprovalIntegrationTest extends TestKitSupport {

  private final TestModelProvider contentModel = new TestModelProvider();
  private final TestModelProvider publishingModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(ContentAgent.class, contentModel)
      .withModelProvider(PublishingAgent.class, publishingModel);
  }

  @Test
  public void shouldCompletePublishingPipelineWithApproval() {
    // Script content agent to produce a draft
    contentModel.fixedResponse(
      new TestModelProvider.AiResponse(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"title\":\"AI in 2026\",\"content\":\"AI is transforming everything.\"}"
        )
      )
    );

    // Script publishing agent to produce a published post
    publishingModel.fixedResponse(
      new TestModelProvider.AiResponse(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"url\":\"https://blog.example.com/ai-2026\",\"publishedAt\":\"2026-03-26T12:00:00Z\"}"
        )
      )
    );

    // Create the 3-task pipeline
    var pipeline = httpClient
      .POST("/publishing")
      .withRequestBody(new PublishingEndpoint.PublishRequest("AI in 2026"))
      .responseBodyAs(PublishingEndpoint.PublishingPipeline.class)
      .invoke()
      .body();

    assertThat(pipeline.draftTaskId()).isNotBlank();
    assertThat(pipeline.approvalTaskId()).isNotBlank();
    assertThat(pipeline.publishTaskId()).isNotBlank();

    // Wait for draft to complete
    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var draft = componentClient
          .forTask(pipeline.draftTaskId())
          .get(PublishingTasks.DRAFT);
        assertThat(draft.status().name()).isEqualTo("COMPLETED");
        assertThat(draft.result()).isNotNull();
        assertThat(draft.result().title()).isEqualTo("AI in 2026");
      });

    // Human approves the draft
    httpClient
      .POST("/publishing/approve/" + pipeline.approvalTaskId())
      .withRequestBody(
        new PublishingEndpoint.ApproveRequest("editor@example.com", "Looks good!")
      )
      .responseBodyAs(String.class)
      .invoke();

    // Wait for publish task to complete
    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var published = componentClient
          .forTask(pipeline.publishTaskId())
          .get(PublishingTasks.PUBLISH);
        assertThat(published.status().name()).isEqualTo("COMPLETED");
        assertThat(published.result()).isNotNull();
        assertThat(published.result().url()).isEqualTo("https://blog.example.com/ai-2026");
      });
  }

  @Test
  public void shouldStopPipelineOnRejection() {
    // Script content agent to produce a draft
    contentModel.fixedResponse(
      new TestModelProvider.AiResponse(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"title\":\"Bad Post\",\"content\":\"This is not good enough.\"}"
        )
      )
    );

    // Create the 3-task pipeline
    var pipeline = httpClient
      .POST("/publishing")
      .withRequestBody(new PublishingEndpoint.PublishRequest("Bad topic"))
      .responseBodyAs(PublishingEndpoint.PublishingPipeline.class)
      .invoke()
      .body();

    // Wait for draft to complete
    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var draft = componentClient
          .forTask(pipeline.draftTaskId())
          .get(PublishingTasks.DRAFT);
        assertThat(draft.status().name()).isEqualTo("COMPLETED");
      });

    // Human rejects the draft
    httpClient
      .POST("/publishing/reject/" + pipeline.approvalTaskId())
      .withRequestBody(
        new PublishingEndpoint.RejectRequest("editor@example.com", "Content quality too low")
      )
      .responseBodyAs(String.class)
      .invoke();

    // Verify approval task is FAILED
    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var approval = componentClient
          .forTask(pipeline.approvalTaskId())
          .get(PublishingTasks.APPROVAL);
        assertThat(approval.status().name()).isEqualTo("FAILED");
        assertThat(approval.failureReason()).isEqualTo("Content quality too low");
      });

    // Verify publish task never started (still PENDING — its dependency failed)
    var publishSnapshot = componentClient
      .forTask(pipeline.publishTaskId())
      .get(PublishingTasks.PUBLISH);
    assertThat(publishSnapshot.status().name()).isIn("PENDING", "ASSIGNED");
  }
}
