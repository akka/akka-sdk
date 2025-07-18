package demo.multiagent.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.multiagent.application.*;
import demo.multiagent.domain.AgentSelection;
import demo.multiagent.domain.Plan;
import demo.multiagent.domain.PlanStep;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class ActivityEndpointIntegrationTest extends TestKitSupport {

  private final TestModelProvider selectorModel = new TestModelProvider();
  private final TestModelProvider plannerModel = new TestModelProvider();
  private final TestModelProvider activitiesModel = new TestModelProvider();
  private final TestModelProvider weatherModel = new TestModelProvider();
  private final TestModelProvider summaryModel = new TestModelProvider();
  private final TestModelProvider evaluatorModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(SelectorAgent.class, selectorModel)
      .withModelProvider(PlannerAgent.class, plannerModel)
      .withModelProvider(ActivityAgent.class, activitiesModel)
      .withModelProvider(WeatherAgent.class, weatherModel)
      .withModelProvider(SummarizerAgent.class, summaryModel)
      .withModelProvider(EvaluatorAgent.class, evaluatorModel);
  }

  @Test
  public void shouldHandleFullActivitySuggestionWorkflowWithPreferenceUpdate() {
    var userId = "alice";
    var query = "I am in Stockholm. What should I do? Beware of the weather";

    // Setup initial AI model responses
    setupInitialModelResponses();

    // 1. Call suggestActivities endpoint
    var suggestResponse = httpClient
      .POST("/activities/" + userId)
      .withRequestBody(new ActivityEndpoint.Request(query))
      .invoke();

    assertThat(suggestResponse.status()).isEqualTo(StatusCodes.CREATED);

    // Extract sessionId from Location header
    var locationHeader = suggestResponse
      .httpResponse()
      .getHeader("Location")
      .orElseThrow()
      .value();
    var sessionId = extractSessionIdFromLocation(locationHeader, userId);

    // 2. Retrieve answer using the sessionId
    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var answerResponse = httpClient
          .GET("/activities/" + userId + "/" + sessionId)
          .responseBodyAs(String.class)
          .invoke();
        assertThat(answerResponse.status()).isEqualTo(StatusCodes.OK);

        var answer = answerResponse.body();
        assertThat(answer).isNotBlank();
        assertThat(answer).contains("Stockholm");
        assertThat(answer).contains("sunny");
        assertThat(answer).contains("bike tour");
      });

    // 3. Retrieve via listActivities
    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var listResponse = httpClient
          .GET("/activities/" + userId)
          .responseBodyAs(ActivityEndpoint.ActivitiesList.class)
          .invoke();
        assertThat(listResponse.status()).isEqualTo(StatusCodes.OK);

        var activitiesList = listResponse.body();
        assertThat(activitiesList.suggestions()).hasSize(1);

        var suggestion = activitiesList.suggestions().getFirst();
        assertThat(suggestion.userQuestion()).isEqualTo(query);
        assertThat(suggestion.answer()).contains("bike tour");
      });

    // 4. Add preference that invalidates previous suggestion
    setupUpdatedModelResponsesForPreference();

    var preferenceResponse = httpClient
      .POST("/preferences/" + userId)
      .withRequestBody(
        new ActivityEndpoint.AddPreference(
          "I hate outdoor activities and prefer indoor museums"
        )
      )
      .invoke();

    assertThat(preferenceResponse.status()).isEqualTo(StatusCodes.CREATED);

    // Wait for preference to be processed and new suggestion generated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var updatedAnswerResponse = httpClient
          .GET("/activities/" + userId + "/" + sessionId)
          .responseBodyAs(String.class)
          .invoke();
        assertThat(updatedAnswerResponse.status()).isEqualTo(StatusCodes.OK);

        var updatedAnswer = updatedAnswerResponse.body();
        // 5. Verify the new suggestion reflects the preference
        assertThat(updatedAnswer).contains("Vasa Museum");
        assertThat(updatedAnswer).contains("indoor");
        assertThat(updatedAnswer).doesNotContain("bike tour");
      });
  }

  private void setupInitialModelResponses() {
    var selection = new AgentSelection(List.of("activity-agent", "weather-agent"));
    selectorModel.fixedResponse(JsonSupport.encodeToString(selection));

    var weatherQuery = "What is the current weather in Stockholm?";
    var activityQuery =
      "Suggest activities to do in Stockholm considering the current weather.";
    var plan = new Plan(
      List.of(
        new PlanStep("weather-agent", weatherQuery),
        new PlanStep("activity-agent", activityQuery)
      )
    );
    plannerModel.fixedResponse(JsonSupport.encodeToString(plan));

    weatherModel
      .whenMessage(req -> req.equals(weatherQuery))
      .reply("The weather in Stockholm is sunny.");

    activitiesModel
      .whenMessage(req -> req.equals(activityQuery))
      .reply(
        "You can take a bike tour around Djurgården Park, " +
        "visit the Vasa Museum, explore Gamla Stan (Old Town)..."
      );

    summaryModel.fixedResponse(
      "The weather in Stockholm is sunny, so you can enjoy " +
      "outdoor activities like a bike tour around Djurgården Park, " +
      "visiting the Vasa Museum, exploring Gamla Stan (Old Town)"
    );

    // Initial evaluator response (no preference conflict)
    evaluatorModel.fixedResponse(
      """
      {
        "score": 5,
        "feedback": "The suggestion is appropriate for the user."
      }
      """
    );
  }

  private void setupUpdatedModelResponsesForPreference() {
    // Evaluator detects preference conflict and triggers new suggestion
    evaluatorModel.reset(); // FIXME this should not be needed, https://github.com/akka/akka-sdk/pull/730
    evaluatorModel
      .whenMessage(req -> req.contains("hate outdoor activities"))
      .reply(
        """
        {
          "score": 1,
          "feedback": "The previous suggestion conflicts with user preferences for indoor activities. Outdoor bike tours are not suitable."
        }
        """
      );

    // Updated activity suggestion based on preference
    activitiesModel
      .whenMessage(req -> req.contains("hate outdoor activities"))
      .reply(
        "Based on your preference for indoor activities, I recommend visiting the " +
        "Vasa Museum, the ABBA Museum, or exploring the Royal Palace indoor exhibitions."
      );

    // Updated summary reflecting preference
    summaryModel.reset(); // FIXME this should not be needed, https://github.com/akka/akka-sdk/pull/730
    summaryModel
      .whenMessage(req -> req.contains("preference for indoor activities"))
      .reply(
        "Given your preference for indoor activities, Stockholm offers excellent museums " +
        "like the Vasa Museum and ABBA Museum, perfect for a cultural indoor experience."
      );
  }

  private String extractSessionIdFromLocation(String locationHeader, String userId) {
    // Location header format: /activities/{userId}/{sessionId}
    var prefix = "/activities/" + userId + "/";
    if (locationHeader.startsWith(prefix)) {
      return locationHeader.substring(prefix.length());
    }
    throw new IllegalArgumentException("Invalid location header format: " + locationHeader);
  }
}
