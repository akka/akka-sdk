package agent_guide.part4;

import agent_guide.part2.ActivityAgent;
import agent_guide.part3.WeatherAgent;
// tag::all[]
import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("agent-team")
public class AgentTeamWorkflow extends Workflow<AgentTeamWorkflow.State> {

  private static final Logger logger = LoggerFactory.getLogger(AgentTeamWorkflow.class);

  public record Request(String userId, String message) {}

  public record State(String userId, String userQuery, String finalAnswer) {
    State withAnswer(String a) {
      return new State(userId, userQuery, a);
    }
  }

  private final ComponentClient componentClient;

  public AgentTeamWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Done> start(Request request) {
    return effects()
      .updateState(new State(request.userId(), request.message(), "")) // <1>
      .transitionTo("weather") // <2>
      .thenReply(Done.getInstance());
  }

  public Effect<String> getAnswer() {
    if (currentState() == null || currentState().finalAnswer.isEmpty()) {
      return effects()
        .error(
          "Workflow '" + commandContext().workflowId() + "' not started, or not completed"
        );
    } else {
      return effects().reply(currentState().finalAnswer);
    }
  }

  @Override
  public WorkflowDef<State> definition() {
    return workflow()
      .addStep(askWeather())
      .addStep(suggestActivities())
      .addStep(error())
      .defaultStepRecoverStrategy(maxRetries(2).failoverTo("error"));
  }

  private Step askWeather() { // <3>
    return step("weather")
      .call(
        () ->
          componentClient
            .forAgent()
            .inSession(sessionId())
            .method(WeatherAgent::query)
            .invoke(currentState().userQuery)
      )
      .andThen(String.class, forecast -> {
        logger.info("Weather forecast: {}", forecast);

        return effects().transitionTo("activities"); // <4>
      })
      .timeout(Duration.ofSeconds(60));
  }

  private Step suggestActivities() {
    return step("activities")
      .call(() ->
        componentClient
          .forAgent()
          .inSession(sessionId())
          .method(ActivityAgent::query) // <5>
          .invoke(
            new ActivityAgent.Request(currentState().userId(), currentState().userQuery())
          ))
      .andThen(String.class, suggestion -> {
        logger.info("Activities: {}", suggestion);

        return effects()
          .updateState(currentState().withAnswer(suggestion)) // <6>
          .end();
      })
      .timeout(Duration.ofSeconds(60));
  }

  private Step error() {
    return step("error").call(() -> null).andThen(() -> effects().end());
  }

  private String sessionId() {
    // the workflow corresponds to the session
    return commandContext().workflowId();
  }
}
// end::all[]
