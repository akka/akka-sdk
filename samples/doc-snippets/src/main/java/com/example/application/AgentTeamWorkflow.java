package com.example.application;

import static java.time.Duration.ofSeconds;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::all[]
@ComponentId("agent-team")
public class AgentTeamWorkflow extends Workflow<AgentTeamWorkflow.State> {

  private static final Logger logger = LoggerFactory.getLogger(AgentTeamWorkflow.class);

  public record State(String userQuery, String weatherForecast, String answer) {
    State withWeatherForecast(String f) {
      return new State(userQuery, f, answer);
    }

    State withAnswer(String a) {
      return new State(userQuery, weatherForecast, a);
    }
  }

  private final ComponentClient componentClient;

  public AgentTeamWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Done> start(String query) {
    return effects()
      .updateState(new State(query, "", ""))
      .transitionTo(AgentTeamWorkflow::askWeather) // <1>
      .thenReply(Done.getInstance());
  }

  public Effect<String> getAnswer() {
    if (currentState() == null || currentState().answer.isEmpty()) {
      String workflowId = commandContext().workflowId();
      return effects().error("Workflow '" + workflowId + "' not started, or not completed");
    } else {
      return effects().reply(currentState().answer);
    }
  }

  @Override
  public WorkflowConfig configuration() {
    return WorkflowConfig.builder()
      .stepConfig(AgentTeamWorkflow::askWeather, ofSeconds(60))
      .stepConfig(AgentTeamWorkflow::suggestActivities, ofSeconds(60))
      .defaultStepRecovery(maxRetries(2).failoverTo(AgentTeamWorkflow::error))
      .build();
  }

  @StepName("weather")
  private StepEffect askWeather() { // <2>
    var forecast = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(WeatherAgent::query)
      .invoke(currentState().userQuery);

    logger.info("Weather forecast: {}", forecast);

    return stepEffects()
      .updateState(currentState().withWeatherForecast(forecast)) // <3>
      .thenTransitionTo(AgentTeamWorkflow::suggestActivities);
  }

  @StepName("activities")
  private StepEffect suggestActivities() {
    // prettier-ignore
    var request = // <4>
      currentState().userQuery +
        "\nWeather forecast: " + currentState().weatherForecast;

    var suggestion = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(ActivityAgent::query)
      .invoke(request);

    logger.info("Activities: {}", suggestion);

    return stepEffects()
      .updateState(currentState().withAnswer(suggestion)) // <5>
      .thenEnd();
  }

  private StepEffect error() {
    return stepEffects().thenEnd();
  }

  private String sessionId() {
    // the workflow corresponds to the session
    return commandContext().workflowId();
  }
}
// end::all[]
