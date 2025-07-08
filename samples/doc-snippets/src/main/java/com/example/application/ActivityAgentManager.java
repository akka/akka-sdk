package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::all[]
@ComponentId("activity-agent-manager")
public class ActivityAgentManager extends Workflow<ActivityAgentManager.State> { // <1>

  // end::all[]
  private static final Logger logger = LoggerFactory.getLogger(ActivityAgentManager.class);

  // tag::all[]

  public record State(String userQuery, String answer) { // <2>
    State withAnswer(String a) {
      return new State(userQuery, a);
    }
  }

  private final ComponentClient componentClient;

  public ActivityAgentManager(ComponentClient componentClient) { // <3>
    this.componentClient = componentClient;
  }

  public Effect<Done> start(String query) { // <4>
    return effects()
      .updateState(new State(query, ""))
      .transitionTo("activities")
      .thenReply(Done.getInstance());
  }

  public ReadOnlyEffect<String> getAnswer() { // <5>
    if (currentState() == null || currentState().answer.isEmpty()) {
      return effects()
        .error(
          "Workflow '" + commandContext().workflowId() + "' not started, or not completed"
        );
    } else {
      return effects().reply(currentState().answer);
    }
  }

  @Override
  public WorkflowDef<State> definition() { // <6>
    return workflow()
      .addStep(suggestActivities())
      .addStep(error())
      .defaultStepRecoverStrategy(maxRetries(2).failoverTo("error"));
  }

  private Step suggestActivities() { // <7>
    return step("activities")
      .call(() ->
        componentClient
          .forAgent()
          .inSession(sessionId())
          .method(ActivityAgent::query) // <8>
          .invoke(currentState().userQuery))
      .andThen(String.class, suggestion -> {
        logger.info("Activities: {}", suggestion);

        return effects()
          .updateState(currentState().withAnswer(suggestion)) // <9>
          .end();
      })
      .timeout(Duration.ofSeconds(60));
  }

  private Step error() {
    return step("error").call(Done::getInstance).andThen(() -> effects().end());
  }

  private String sessionId() { // <10>
    // the workflow corresponds to the session
    return commandContext().workflowId();
  }
}
// end::all[]
