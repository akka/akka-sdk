package demo.multiagent.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.multiagent.application.ActivityAgent;
import demo.multiagent.application.ActivityCoordinator;
import demo.multiagent.application.CoordinatorModels;
import demo.multiagent.application.WeatherAgent;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class ActivityEndpointIntegrationTest extends TestKitSupport {

  private final TestModelProvider coordinatorModel = new TestModelProvider();
  private final TestModelProvider activitiesModel = new TestModelProvider();
  private final TestModelProvider weatherModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      """
      akka.javasdk.agent.openai.api-key = n/a
      # the evaluator of the coordinator is tested in ActivityEvaluatorIntegrationTest
      akka.javasdk.evaluation.evaluators.activity-evaluator.agents.activity-coordinator.enabled = false
      """
    )
      .withModelProvider(ActivityCoordinator.class, coordinatorModel)
      .withModelProvider(ActivityAgent.class, activitiesModel)
      .withModelProvider(WeatherAgent.class, weatherModel);
  }

  @Test
  public void shouldRunCoordinatorAndReturnAnswer() {
    var userId = "alice";
    var query = "I am in Stockholm. What should I do? Beware of the weather";

    CoordinatorModels.scriptStockholm(coordinatorModel, weatherModel, activitiesModel);

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
}
