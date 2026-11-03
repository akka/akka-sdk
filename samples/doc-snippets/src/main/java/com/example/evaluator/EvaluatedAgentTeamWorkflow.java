package com.example.evaluator;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.Subject;
import akka.javasdk.workflow.Workflow;
import com.example.application.ActivityAgent;
import com.example.application.WeatherAgent;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// tag::all[]
@Component(id = "evaluated-agent-team")
public class EvaluatedAgentTeamWorkflow extends Workflow<EvaluatedAgentTeamWorkflow.State> {

  public record State(
    String userQuery,
    String weatherForecast,
    List<String> interactionIds, // <1>
    String answer
  ) {
    State withWeatherForecast(String interactionId, String forecast) {
      return new State(userQuery, forecast, withInteraction(interactionId), answer);
    }

    State withAnswer(String interactionId, String answer) {
      return new State(userQuery, weatherForecast, withInteraction(interactionId), answer);
    }

    private List<String> withInteraction(String interactionId) {
      return Stream.concat(interactionIds.stream(), Stream.of(interactionId)).toList();
    }
  }

  private final ComponentClient componentClient;

  public EvaluatedAgentTeamWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Done> start(String query) {
    return effects()
      .updateState(new State(query, "", List.of(), ""))
      .transitionTo(EvaluatedAgentTeamWorkflow::askWeather)
      .thenReply(Done.getInstance());
  }

  public Effect<String> getAnswer() {
    return effects().reply(currentState().answer());
  }

  private StepEffect askWeather() {
    var reply = componentClient
      .forAgent()
      .inSession(commandContext().workflowId() + "-weather")
      .method(WeatherAgent::query)
      .withDetailedReply() // <2>
      .invoke(currentState().userQuery());

    return stepEffects()
      .updateState(
        currentState().withWeatherForecast(reply.interactionId().orElseThrow(), reply.value())
      )
      .thenTransitionTo(EvaluatedAgentTeamWorkflow::suggestActivities);
  }

  private StepEffect suggestActivities() {
    var request =
      currentState().userQuery() + "\nWeather forecast: " + currentState().weatherForecast();
    var reply = componentClient
      .forAgent()
      .inSession(commandContext().workflowId() + "-activities")
      .method(ActivityAgent::query)
      .withDetailedReply()
      .invoke(request);

    return stepEffects()
      .updateState(
        currentState().withAnswer(reply.interactionId().orElseThrow(), reply.value())
      )
      .thenTransitionTo(EvaluatedAgentTeamWorkflow::evaluate);
  }

  private StepEffect evaluate() { // <3>
    componentClient
      .forEvaluator(AgentTeamRunEvaluator.class)
      .startEvaluation(
        new Subject.Workflow(commandContext().workflowId(), "evaluated-agent-team"), // <4>
        Map.of("interactionIds", String.join(",", currentState().interactionIds())) // <5>
      );
    return stepEffects().thenEnd();
  }
}
// end::all[]
