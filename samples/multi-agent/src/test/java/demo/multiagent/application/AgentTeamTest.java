package demo.multiagent.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.multiagent.application.agents.ActivityAgent;
import demo.multiagent.application.agents.Planner;
import demo.multiagent.application.agents.Selector;
import demo.multiagent.application.agents.Summarizer;
import demo.multiagent.application.agents.WeatherAgent;
import demo.multiagent.domain.AgentResponse;
import demo.multiagent.domain.AgentSelection;
import demo.multiagent.domain.Plan;
import demo.multiagent.domain.PlanStep;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

// tag::class[]
public class AgentTeamTest extends TestKitSupport { // <1>

  private final TestModelProvider selectorModel = new TestModelProvider(); // <2>
  private final TestModelProvider plannerModel = new TestModelProvider();
  private final TestModelProvider activitiesModel = new TestModelProvider();
  private final TestModelProvider weatherModel = new TestModelProvider();
  private final TestModelProvider summaryModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(Selector.class, selectorModel) // <3>
        .withModelProvider(Planner.class, plannerModel)
        .withModelProvider(ActivityAgent.class, activitiesModel)
        .withModelProvider(WeatherAgent.class, weatherModel)
        .withModelProvider(Summarizer.class, summaryModel);
  }

  @Test
  public void test() {
    var selection = new AgentSelection(List.of("activity-agent", "weather-agent"));
    selectorModel.fixedResponse(JsonSupport.encodeToString(selection)); // <4>

    var weatherQuery = "What is the current weather in Stockholm?";
    var activityQuery = "Suggest activities to do in Stockholm considering the current weather.";
    var plan = new Plan(List.of(
        new PlanStep("weather-agent", weatherQuery),
        new PlanStep("activity-agent", activityQuery)));
    plannerModel.fixedResponse(JsonSupport.encodeToString(plan));

    weatherModel
        .whenMessage(req -> req.equals(weatherQuery)) // <5>
        .reply(
            JsonSupport.encodeToString(
                new AgentResponse("The weather in Stockholm is sunny.", null)));

    activitiesModel
        .whenMessage(req -> req.equals(activityQuery))
        .reply(
            JsonSupport.encodeToString(
                new AgentResponse(
                    "You can take a bike tour around Djurgården Park, "
                        + "visit the Vasa Museum, explore Gamla Stan (Old Town)...",
                    null)));

    summaryModel.fixedResponse("The weather in Stockholm is sunny, so you can enjoy " +
        "outdoor activities like a bike tour around Djurgården Park, visiting the Vasa Museum, " +
        "exploring Gamla Stan (Old Town)");

    var query = "I am in Stockholm. What should I do? Beware of the weather";

    var sessionId = UUID.randomUUID().toString();
    componentClient.forWorkflow(sessionId).method(AgentTeam::start).invoke(query); // <6>

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, SECONDS)
        .untilAsserted(() -> {
          var answer = componentClient.forWorkflow(sessionId).method(AgentTeam::getAnswer).invoke();
          assertThat(answer).isNotBlank();
          assertThat(answer).contains("Stockholm");
          assertThat(answer).contains("sunny");
          assertThat(answer).contains("bike tour");
        });
  }
}
// end::class[]
