package demo.activities.application;

import static demo.activities.application.ActivityQuestions.NEEDS_WEATHER;
import static demo.activities.application.ActivityQuestions.QUALITY;
import static demo.activities.application.ActivityQuestions.RESPECTS_PREFERENCES;
import static demo.activities.application.ActivityQuestions.TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.decision.DecisionModelProvider;
import akka.javasdk.testkit.TestDecisionModelProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class ActivityWorkflowIntegrationTest extends TestKitSupport {

  private static final String MUSEUM = "Visit the Vasa Museum.";
  private static final String BIKE = "Take a bike tour around Djurgården.";

  private final TestModelProvider weatherModel = new TestModelProvider();
  private final TestModelProvider activityModel = new TestModelProvider();
  private final TestDecisionModelProvider decisionModel = new TestDecisionModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(WeatherAgent.class, weatherModel)
      .withModelProvider(ActivityAgent.class, activityModel)
      .withDecisionModelProvider(decisionModel);
  }

  @Test
  public void shouldAskAgainWhenTheSuggestionIgnoresAPreference() {
    var message = "I am in Stockholm. What should I do?";
    componentClient
      .forEventSourcedEntity("alice")
      .method(PreferencesEntity::addPreference)
      .invoke(new PreferencesEntity.AddPreference("I do not like museums."));

    decisionModel
      .whenInput(message)
      .choice(TOPIC, "activities")
      .predicate(NEEDS_WEATHER, 0.9);
    decisionModel
      .whenRequest(request -> isCheckOf(request, MUSEUM))
      .score(QUALITY, 2)
      .predicate(RESPECTS_PREFERENCES, 0.1);
    decisionModel
      .whenRequest(request -> isCheckOf(request, BIKE))
      .score(QUALITY, 2)
      .predicate(RESPECTS_PREFERENCES, 0.95);

    weatherModel.fixedResponse("Sunny and 24 °C in Stockholm.");
    activityModel.whenMessage(msg -> !msg.contains("Feedback")).reply(MUSEUM);
    activityModel.whenMessage(msg -> msg.contains("Feedback")).reply(BIKE);

    var result = run("alice", message);

    assertThat(result.answer()).isEqualTo(BIKE);
    assertThat(result.failure()).isEmpty();
    assertThat(result.decisions())
      .extracting(ActivityWorkflow.StepDecision::step)
      .containsExactly("classify", "check", "check");
  }

  @Test
  public void shouldNotAskTheAgentsAboutAnotherTopic() {
    var message = "What is the capital of Sweden?";
    decisionModel.whenInput(message).choice(TOPIC, "other").predicate(NEEDS_WEATHER, 0.1);

    var result = run("bob", message);

    assertThat(result.answer()).startsWith("I can only suggest activities");
    assertThat(result.decisions())
      .extracting(ActivityWorkflow.StepDecision::step)
      .containsExactly("classify");
  }

  private ActivityWorkflow.Result run(String userId, String message) {
    var workflowId = UUID.randomUUID().toString();
    componentClient
      .forWorkflow(workflowId)
      .method(ActivityWorkflow::start)
      .invoke(new ActivityWorkflow.Request(userId, message));

    return Awaitility.await()
      .atMost(20, TimeUnit.SECONDS)
      .until(
        () ->
          componentClient
            .forWorkflow(workflowId)
            .method(ActivityWorkflow::getResult)
            .invoke(),
        result -> !result.answer().isEmpty() || !result.failure().isEmpty()
      );
  }

  private static boolean isCheckOf(
    DecisionModelProvider.DecisionRequest request,
    String suggestion
  ) {
    return (
      request.input() instanceof ActivityWorkflow.SuggestionCheck check &&
      check.suggestion().equals(suggestion)
    );
  }
}
