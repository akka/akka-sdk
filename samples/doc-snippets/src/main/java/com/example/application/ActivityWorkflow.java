package com.example.application;

import static java.time.Duration.ofMillis;

import akka.javasdk.NotificationPublisher;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akka.stream.Materializer;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

// tag::workflow-agent-stream-notification[]
@Component(id = "activity")
public class ActivityWorkflow extends Workflow<ActivityWorkflow.State> {

  // end::workflow-agent-stream-notification[]
  public record State(String userQuery, String weatherForecast, String answer) {
    State withWeatherForecast(String f) {
      return new State(userQuery, f, answer);
    }

    State withAnswer(String a) {
      return new State(userQuery, weatherForecast, a);
    }
  }

  // tag::workflow-agent-stream-notification[]
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
  @JsonSubTypes(
    {
      @JsonSubTypes.Type(value = ActivityWorkflowNotification.StatusUpdate.class, name = "S"),
      @JsonSubTypes.Type(
        value = ActivityWorkflowNotification.LlmResponseStart.class,
        name = "LS"
      ),
      @JsonSubTypes.Type(
        value = ActivityWorkflowNotification.LlmResponseDelta.class,
        name = "LD"
      ),
      @JsonSubTypes.Type(
        value = ActivityWorkflowNotification.LlmResponseEnd.class,
        name = "LE"
      ),
    }
  )
  public sealed interface ActivityWorkflowNotification { // <1>
    record StatusUpdate(String msg) implements ActivityWorkflowNotification {}

    record LlmResponseStart() implements ActivityWorkflowNotification {}

    record LlmResponseDelta(String response) implements ActivityWorkflowNotification {}

    record LlmResponseEnd() implements ActivityWorkflowNotification {}
  }

  private final ComponentClient componentClient;
  private final NotificationPublisher<ActivityWorkflowNotification> notificationPublisher;
  private final Materializer materializer;

  public ActivityWorkflow(
    ComponentClient componentClient,
    NotificationPublisher<ActivityWorkflowNotification> notificationPublisher, // <2>
    Materializer materializer
  ) {
    this.componentClient = componentClient;
    this.notificationPublisher = notificationPublisher;
    this.materializer = materializer;
  }

  @StepName("summarize")
  private StepEffect summarizeStep(String request) {
    var tokenSource = componentClient // <3>
      .forAgent()
      .inSession(sessionId())
      .tokenStream(SummarizerAgent::summarize)
      .source(request);

    notificationPublisher.publish(new ActivityWorkflowNotification.LlmResponseStart()); // <4>

    var finalAnswer = notificationPublisher.publishTokenStream(
      tokenSource, // <5>
      10,
      ofMillis(200),
      ActivityWorkflowNotification.LlmResponseDelta::new,
      materializer
    );

    notificationPublisher.publish(new ActivityWorkflowNotification.LlmResponseEnd()); // <4>
    notificationPublisher.publish(
      new ActivityWorkflowNotification.StatusUpdate("All steps completed!")
    ); // <4>

    return stepEffects()
      .updateState(currentState().withAnswer(finalAnswer)) // <6>
      .thenPause();
  }

  // end::workflow-agent-stream-notification[]

  private String sessionId() {
    // the workflow corresponds to the session
    return commandContext().workflowId();
  }
  // tag::workflow-agent-stream-notification[]
}
// end::workflow-agent-stream-notification[]
