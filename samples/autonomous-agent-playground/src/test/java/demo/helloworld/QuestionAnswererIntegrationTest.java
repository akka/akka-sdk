package demo.helloworld;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.failTask;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.helloworld.api.QuestionEndpoint;
import demo.helloworld.application.Answer;
import demo.helloworld.application.QuestionAnswerer;
import demo.helloworld.application.QuestionTasks;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

// tag::class[]
public class QuestionAnswererIntegrationTest extends TestKitSupport {

  private final TestModelProvider model = new TestModelProvider(); // <1>

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    ).withModelProvider(QuestionAnswerer.class, model); // <2>
  }

  @Test
  public void shouldAnswerQuestionWithTypedResult() {
    model.fixedResponse( // <3>
      new TestModelProvider.AiResponse(completeTask(new Answer("2 plus 2 equals 4.", 100)))
    );

    var response = httpClient // <4>
      .POST("/questions")
      .withRequestBody(new QuestionEndpoint.AskQuestion("What is 2 + 2?"))
      .responseBodyAs(QuestionEndpoint.QuestionResponse.class)
      .invoke()
      .body();

    var taskId = response.id();
    assertThat(taskId).isNotBlank();
    assertThat(response.runId()).isNotBlank();
    assertThat(response.agentComponentId()).isEqualTo("question-answerer");

    Awaitility.await() // <5>
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(taskId).get(QuestionTasks.ANSWER);
        assertThat(snapshot.result()).isNotNull();
        assertThat(snapshot.result().answer()).isEqualTo("2 plus 2 equals 4.");
        assertThat(snapshot.result().confidence()).isEqualTo(100);
      });
  }
  // end::class[]

  // tag::failure[]
  @Test
  public void shouldFailTaskWhenModelCallsFailTask() {
    model.fixedResponse(
      new TestModelProvider.AiResponse(failTask("I cannot answer this question."))
    );

    var response = httpClient
      .POST("/questions")
      .withRequestBody(new QuestionEndpoint.AskQuestion("What is the meaning of life?"))
      .responseBodyAs(QuestionEndpoint.QuestionResponse.class)
      .invoke()
      .body();

    var taskId = response.id();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(taskId).get(QuestionTasks.ANSWER);
        assertThat(snapshot.status().name()).isEqualTo("FAILED");
        assertThat(snapshot.failureReason()).isEqualTo("I cannot answer this question.");
      });
  }
  // end::failure[]
}
