package demo.ui;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
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
import org.junit.jupiter.api.Test;

public class RunControlEndpointStopTest extends TestKitSupport {

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    ).withModelProvider(QuestionAnswerer.class, model);
  }

  @Test
  public void stopReturns404ForUnknownComponent() {
    var response = httpClient
      .POST("/playground/api/runs/" + UUID.randomUUID() + "/stop?component=does-not-exist")
      .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.NOT_FOUND);
  }

  @Test
  public void stopAcceptsAndReportsCancelled() {
    // Submit a real run so the agent instance exists.
    model.fixedResponse(new TestModelProvider.AiResponse(completeTask(new Answer("42", 50))));
    var post = httpClient
      .POST("/questions")
      .withRequestBody(new QuestionEndpoint.AskQuestion("anything"))
      .responseBodyAs(QuestionEndpoint.QuestionResponse.class)
      .invoke()
      .body();

    var stopResp = httpClient
      .POST(
        "/playground/api/runs/" + post.runId() + "/stop?component=" + post.agentComponentId()
      )
      .responseBodyAs(RunControlEndpoint.StopResponse.class)
      .invoke()
      .body();

    assertThat(stopResp.runState()).isEqualTo("CANCELLED");
    assertThat(stopResp.stoppedAt()).isNotNull();
  }

  @Test
  public void stopIsIdempotent() {
    model.fixedResponse(new TestModelProvider.AiResponse(completeTask(new Answer("42", 50))));
    var post = httpClient
      .POST("/questions")
      .withRequestBody(new QuestionEndpoint.AskQuestion("anything"))
      .responseBodyAs(QuestionEndpoint.QuestionResponse.class)
      .invoke()
      .body();

    var url =
      "/playground/api/runs/" + post.runId() + "/stop?component=" + post.agentComponentId();
    var first = httpClient.POST(url).invoke();
    var second = httpClient.POST(url).invoke();

    assertThat(first.status()).isEqualTo(StatusCodes.OK);
    assertThat(second.status()).isEqualTo(StatusCodes.OK);
  }
}
