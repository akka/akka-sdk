package patterns;

import agent_guide.part3.WeatherAgent;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import java.util.concurrent.TimeUnit;

@Component(id = "concurrent-agent-team")
public class ConcurrentWorkflow extends Workflow<ConcurrentWorkflow.State> {

  public record State(
    String userId,
    String userQuery,
    String weather,
    String traffic,
    String finalAnswer
  ) {
    State withWeather(String w) {
      return new State(userId, userQuery, w, traffic, finalAnswer);
    }

    State withTraffic(String t) {
      return new State(userId, userQuery, weather, t, finalAnswer);
    }

    State withAnswer(String a) {
      return new State(userId, userQuery, weather, traffic, a);
    }
  }

  private final ComponentClient componentClient;

  public ConcurrentWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::concurrent-step[]
  private StepEffect askWeatherAndTraffic() throws Exception {
    // call WeatherAgent and TrafficAgent in parallel

    var forecastCall = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(WeatherAgent::query)
      .invokeAsync(currentState().userQuery); // <1>

    var trafficAlertsCall = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(TrafficAgent::query)
      .invokeAsync(currentState().userQuery); // <1>

    // collect the results
    var forecast = forecastCall.toCompletableFuture().get(30, TimeUnit.SECONDS); // <2>
    var trafficAlerts = trafficAlertsCall.toCompletableFuture().get(30, TimeUnit.SECONDS); // <2>

    return stepEffects()
      .updateState(currentState().withWeather(forecast).withTraffic(trafficAlerts))
      .thenTransitionTo(ConcurrentWorkflow::suggestActivities); // <3>
  }

  // end::concurrent-step[]

  private StepEffect suggestActivities() {
    return stepEffects().thenEnd();
  }

  private String sessionId() {
    // the workflow corresponds to the session
    return commandContext().workflowId();
  }
}
