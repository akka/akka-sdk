package demo.ui;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.failTask;
import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.helloworld.api.QuestionEndpoint;
import demo.helloworld.application.Answer;
import demo.helloworld.application.QuestionAnswerer;
import demo.ui.api.RunControlEndpoint;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class RunControlEndpointStatusTest extends TestKitSupport {

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    ).withModelProvider(QuestionAnswerer.class, model);
  }

  @Test
  public void returns404ForUnknownComponent() {
    var fakeRunId = UUID.randomUUID().toString();
    var fakeTaskId = UUID.randomUUID().toString();
    var response = httpClient
      .GET(
        "/playground/api/runs/" +
        fakeRunId +
        "/status?component=does-not-exist&task=" +
        fakeTaskId +
        "&sample=helloworld"
      )
      .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.NOT_FOUND);
  }

  @Test
  public void returns400WhenRequiredQueryParamMissing() {
    var response = httpClient
      .GET(
        "/playground/api/runs/" + UUID.randomUUID() + "/status?component=question-answerer"
      )
      .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.BAD_REQUEST);
  }

  @Test
  public void returns404ForUnknownSample() {
    var response = httpClient
      .GET(
        "/playground/api/runs/" +
        UUID.randomUUID() +
        "/status?component=question-answerer&task=" +
        UUID.randomUUID() +
        "&sample=not-a-sample"
      )
      .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.NOT_FOUND);
  }

  @Test
  public void runStateBecomesCompletedAfterTaskFinishes() {
    model.fixedResponse(
      new TestModelProvider.AiResponse(completeTask(new Answer("Paris.", 99)))
    );

    var post = httpClient
      .POST("/questions")
      .withRequestBody(new QuestionEndpoint.AskQuestion("Capital of France?"))
      .responseBodyAs(QuestionEndpoint.QuestionResponse.class)
      .invoke()
      .body();

    var statusUrl =
      "/playground/api/runs/" +
      post.runId() +
      "/status?component=" +
      post.agentComponentId() +
      "&task=" +
      post.id() +
      "&sample=helloworld";

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var status = httpClient
          .GET(statusUrl)
          .responseBodyAs(RunControlEndpoint.RunStatus.class)
          .invoke()
          .body();
        assertThat(status.runState()).isEqualTo("COMPLETED");
        assertThat(status.taskStatus()).isEqualTo("COMPLETED");
        assertThat(status.runId()).isEqualTo(post.runId());
        assertThat(status.taskId()).isEqualTo(post.id());
        assertThat(status.agentComponentId()).isEqualTo("question-answerer");
        assertThat(status.failureReason()).isNull();
        assertThat(status.finalResult()).isNotNull();
      });
  }

  @Test
  public void runStateBecomesFailedWhenAgentFailsTask() {
    model.fixedResponse(new TestModelProvider.AiResponse(failTask("cannot answer")));

    var post = httpClient
      .POST("/questions")
      .withRequestBody(new QuestionEndpoint.AskQuestion("Meaning of life?"))
      .responseBodyAs(QuestionEndpoint.QuestionResponse.class)
      .invoke()
      .body();

    var statusUrl =
      "/playground/api/runs/" +
      post.runId() +
      "/status?component=" +
      post.agentComponentId() +
      "&task=" +
      post.id() +
      "&sample=helloworld";

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var status = httpClient
          .GET(statusUrl)
          .responseBodyAs(RunControlEndpoint.RunStatus.class)
          .invoke()
          .body();
        assertThat(status.runState()).isEqualTo("FAILED");
        assertThat(status.taskStatus()).isEqualTo("FAILED");
        assertThat(status.failureReason()).isEqualTo("cannot answer");
      });
  }
}
