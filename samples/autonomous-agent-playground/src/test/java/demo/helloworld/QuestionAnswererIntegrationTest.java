package demo.helloworld;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.helloworld.api.QuestionEndpoint.AnswerResponse;
import demo.helloworld.api.QuestionEndpoint.QuestionRequest;
import demo.helloworld.api.QuestionEndpoint.QuestionResponse;
import demo.helloworld.application.QuestionAnswerer;
import demo.helloworld.application.QuestionTasks;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class QuestionAnswererIntegrationTest extends TestKitSupport {

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(QuestionAnswerer.class, model);
  }

  @Test
  public void shouldSubmitQuestionAndGetAnswer() {
    model.fixedResponse(
        new TestModelProvider.AiResponse(
            new TestModelProvider.ToolInvocationRequest(
                "complete_task",
                "{\"answer\":\"2 plus 2 equals 4.\",\"confidence\":95}"
            )
        )
    );

    var submitResponse = httpClient
        .POST("/questions")
        .withRequestBody(new QuestionRequest("What is 2 + 2?"))
        .responseBodyAs(QuestionResponse.class)
        .invoke();

    assertThat(submitResponse.status().isSuccess()).isTrue();
    var taskId = submitResponse.body().taskId();
    assertThat(taskId).isNotEmpty();

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var result = httpClient
              .GET("/questions/" + taskId)
              .responseBodyAs(AnswerResponse.class)
              .invoke();

          assertThat(result.body().status()).isEqualTo("COMPLETED");
          assertThat(result.body().answer()).isEqualTo("2 plus 2 equals 4.");
          assertThat(result.body().confidence()).isEqualTo(95);
        });
  }

  @Test
  public void shouldReturnConsistentTypedResult() {
    model.fixedResponse(
        new TestModelProvider.AiResponse(
            new TestModelProvider.ToolInvocationRequest(
                "complete_task",
                "{\"answer\":\"The sky is blue due to Rayleigh scattering.\",\"confidence\":90}"
            )
        )
    );

    var submitResponse = httpClient
        .POST("/questions")
        .withRequestBody(new QuestionRequest("Why is the sky blue?"))
        .responseBodyAs(QuestionResponse.class)
        .invoke();

    var taskId = submitResponse.body().taskId();

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var result = httpClient
              .GET("/questions/" + taskId)
              .responseBodyAs(AnswerResponse.class)
              .invoke();
          assertThat(result.body().status()).isEqualTo("COMPLETED");
        });

    // Retrieve twice to verify consistency
    var first = httpClient
        .GET("/questions/" + taskId)
        .responseBodyAs(AnswerResponse.class)
        .invoke();

    var second = httpClient
        .GET("/questions/" + taskId)
        .responseBodyAs(AnswerResponse.class)
        .invoke();

    assertThat(first.body().answer()).isEqualTo(second.body().answer());
    assertThat(first.body().confidence()).isEqualTo(second.body().confidence());
  }

  @Test
  public void shouldRejectEmptyQuestion() {
    var exception = org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> httpClient
            .POST("/questions")
            .withRequestBody(new QuestionRequest(""))
            .responseBodyAs(String.class)
            .invoke());

    assertThat(exception.getMessage()).contains("400");
  }

  @Test
  public void shouldRejectUnknownTaskId() {
    var exception = org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> httpClient
            .GET("/questions/nonexistent-task-id")
            .responseBodyAs(String.class)
            .invoke());

    assertThat(exception.getMessage()).contains("400");
  }
}
