package demo.multiagent.api;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.delegateTo;
import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.agent.evaluator.ToxicityEvaluator;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.multiagent.application.ActivityAgent;
import demo.multiagent.application.ActivityCoordinator;
import demo.multiagent.application.EvaluatorAgent;
import demo.multiagent.application.WeatherAgent;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class ActivityEndpointIntegrationTest extends TestKitSupport {

  private final TestModelProvider coordinatorModel = new TestModelProvider();
  private final TestModelProvider activitiesModel = new TestModelProvider();
  private final TestModelProvider weatherModel = new TestModelProvider();
  private final TestModelProvider evaluatorModel = new TestModelProvider();
  private final TestModelProvider toxicityEvalModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(ActivityCoordinator.class, coordinatorModel)
      .withModelProvider(ActivityAgent.class, activitiesModel)
      .withModelProvider(WeatherAgent.class, weatherModel)
      .withModelProvider(EvaluatorAgent.class, evaluatorModel)
      .withModelProvider(ToxicityEvaluator.class, toxicityEvalModel);
  }

  @Test
  public void shouldRunCoordinatorAndReturnAnswer() {
    var userId = "alice";
    var query = "I am in Stockholm. What should I do? Beware of the weather";

    setupModelResponses();

    var suggestResponse = httpClient
      .POST("/activities/" + userId)
      .withRequestBody(new ActivityEndpoint.Request(query))
      .responseBodyAs(String.class)
      .invoke();

    assertThat(suggestResponse.status()).isEqualTo(StatusCodes.CREATED);

    var taskId = suggestResponse.body();
    assertThat(taskId).isNotBlank();

    var locationHeader = suggestResponse
      .httpResponse()
      .getHeader("Location")
      .orElseThrow()
      .value();
    assertThat(locationHeader).isEqualTo("/activities/" + userId + "/" + taskId);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var answerResponse = httpClient
          .GET("/activities/" + userId + "/" + taskId)
          .responseBodyAs(String.class)
          .invoke();
        assertThat(answerResponse.status()).isEqualTo(StatusCodes.OK);

        var answer = answerResponse.body();
        assertThat(answer).isNotBlank();
        assertThat(answer).contains("Stockholm");
        assertThat(answer).contains("sunny");
        assertThat(answer).contains("bike tour");
      });
  }

  private void setupModelResponses() {
    // Coordinator turn 1: the initial task instructions mention the location.
    coordinatorModel
      .whenMessage(msg -> msg.contains("Stockholm"))
      .reply(
        List.of(
          delegateTo(
            WeatherAgent.class,
            "{\"request\":\"What is the current weather in Stockholm?\"}"
          ),
          delegateTo(
            ActivityAgent.class,
            "{\"request\":{\"userId\":\"alice\",\"message\":\"Suggest activities to do in Stockholm considering the current weather.\"}}"
          )
        )
      );

    // Coordinator turn 2+: runtime sends "Continue working ..." or "Reminder ..." prompts
    // after the workers complete. Synthesise the final answer on any such follow-up.
    coordinatorModel
      .whenMessage(msg -> msg.contains("Continue working") || msg.contains("Reminder"))
      .reply(
        completeTask(
          "The weather in Stockholm is sunny, so you can enjoy outdoor activities like " +
          "a bike tour around Djurgården Park, visiting the Vasa Museum, exploring Gamla Stan."
        )
      );

    weatherModel.fixedResponse("The weather in Stockholm is sunny.");

    activitiesModel.fixedResponse(
      "You can take a bike tour around Djurgården Park, " +
      "visit the Vasa Museum, explore Gamla Stan (Old Town)..."
    );

    evaluatorModel.fixedResponse(
      """
      {
        "label": "Correct",
        "explanation": "The suggestion is appropriate for the user."
      }
      """
    );

    toxicityEvalModel.fixedResponse(
      """
      {
        "label" : "non-toxic"
      }
      """.stripIndent()
    );
  }
}
