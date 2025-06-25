package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

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
        .transitionTo("weather") // <1>
        .thenReply(Done.getInstance());
  }

  public Effect<String> getAnswer() {
    if (currentState() == null || currentState().answer.isEmpty()) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started, or not completed");
    } else {
      return effects().reply(currentState().answer);
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

  private Step askWeather() { // <2>
    return step("weather")
        .call(() ->
            componentClient
                .forAgent()
                .inSession(sessionId())
                .method(WeatherAgent::query)
                .invoke(currentState().userQuery))
        .andThen(String.class, forecast -> {
          logger.info("Weather forecast: {}", forecast);

          return effects()
              .updateState(currentState().withWeatherForecast(forecast))// <3>
              .transitionTo("activities");
        })
        .timeout(Duration.ofSeconds(60));
  }

  private Step suggestActivities() {
    return step("activities")
        .call(() -> {
          String request = currentState().userQuery +
              "\nWeather forecast: " + currentState().weatherForecast; // <4>
          return componentClient
              .forAgent()
              .inSession(sessionId())
              .method(ActivityAgent::query)
              .invoke(request);
        })
        .andThen(String.class, suggestion -> {
          logger.info("Activities: {}", suggestion);

          return effects()
              .updateState(currentState().withAnswer(suggestion)) // <5>
              .end();
        })
        .timeout(Duration.ofSeconds(60));
  }

  private Step error() {
    return step("error")
        .call(() -> null)
        .andThen(() -> effects().end());
  }

  private String sessionId() {
    // the workflow corresponds to the session
    return commandContext().workflowId();
  }
}
// end::all[]
