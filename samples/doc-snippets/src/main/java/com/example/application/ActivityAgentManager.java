package com.example.application;

import static java.time.Duration.ofSeconds;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
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
      .transitionTo(ActivityAgentManager::suggestActivities)
      .thenReply(Done.getInstance());
  }

  public ReadOnlyEffect<String> getAnswer() { // <5>
    if (currentState() == null || currentState().answer.isEmpty()) {
      String workflowId = commandContext().workflowId();
      // prettier-ignore
      return effects()
        .error("Workflow '" + workflowId + "' not started, or not completed");
    } else {
      return effects().reply(currentState().answer);
    }
  }

  @Override
  public WorkflowSettings settings() { // <6>
    return WorkflowSettings.builder()
      .stepTimeout(ActivityAgentManager::suggestActivities, ofSeconds(60))
      .defaultStepRecovery(maxRetries(2).failoverTo(ActivityAgentManager::error))
      .build();
  }

  @StepName("activities")
  private StepEffect suggestActivities() { // <7>
    var suggestion = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(ActivityAgent::query) // <8>
      .invoke(currentState().userQuery);

    logger.info("Activities: {}", suggestion);

    return stepEffects()
      .updateState(currentState().withAnswer(suggestion)) // <9>
      .thenEnd();
  }

  private StepEffect error() {
    return stepEffects().thenEnd();
  }

  private String sessionId() { // <10>
    // the workflow corresponds to the session
    return commandContext().workflowId();
  }
}
// end::all[]
