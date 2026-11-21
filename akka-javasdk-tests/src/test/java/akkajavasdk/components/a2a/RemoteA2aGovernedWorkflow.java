/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.a2a;

import akka.Done;
import akka.javasdk.JsonSupport;
import akka.javasdk.a2a.A2aClient;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aSendMessageResponse;
import akka.javasdk.agent.Guardrail;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.WorkflowContext;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Calls one skill of a remote A2A agent from a step and records the reply, or the guardrail that
 * denied the call.
 */
@Component(id = "remote-a2a-governed-workflow")
public class RemoteA2aGovernedWorkflow extends Workflow<RemoteA2aGovernedWorkflow.State> {

  /**
   * @param agent the configured name of the remote agent, or null to use the card URL
   * @param cardUrl the URL of the Agent Card, when no name is given
   */
  public record Call(String agent, String cardUrl, String skill, String text) {}

  /**
   * @param outcome {@code reply: } and the text of the reply, or {@code denied: } and the message
   */
  public record State(Call call, String outcome) {}

  private final A2aClient a2aClient;
  private final WorkflowContext context;

  public RemoteA2aGovernedWorkflow(A2aClient a2aClient, WorkflowContext context) {
    this.a2aClient = a2aClient;
    this.context = context;
  }

  public Effect<Done> start(Call call) {
    return effects()
        .updateState(new State(call, null))
        .transitionTo(RemoteA2aGovernedWorkflow::call)
        .thenReply(Done.getInstance());
  }

  private StepEffect call() {
    var call = currentState().call();
    var remote =
        call.agent() != null
            ? a2aClient.forAgent(call.agent())
            : a2aClient.forCardUrl(call.cardUrl());
    String outcome;
    try {
      var reply =
          remote
              .send(call.skill(), A2aMessage.user(call.text()))
              .inContext(context.workflowId())
              .invoke();
      outcome = "reply: " + text(reply);
    } catch (Guardrail.GuardrailException e) {
      outcome = "denied: " + e.getMessage();
    }
    return stepEffects().updateState(new State(call, outcome)).thenEnd();
  }

  public ReadOnlyEffect<State> get() {
    return effects().reply(currentState());
  }

  private static String text(A2aSendMessageResponse reply) {
    List<A2aPart> parts =
        switch (reply) {
          case A2aSendMessageResponse.Message m -> m.message().parts();
          case A2aSendMessageResponse.Task t ->
              t.task().artifacts().stream().flatMap(a -> a.parts().stream()).toList();
        };
    return parts.stream()
        .map(
            part ->
                switch (part) {
                  case A2aPart.Text t -> t.text();
                  case A2aPart.Data d -> json(d.data());
                  default -> "";
                })
        .collect(Collectors.joining("\n"));
  }

  private static String json(Object data) {
    try {
      return JsonSupport.getObjectMapper().writeValueAsString(data);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
