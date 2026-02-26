/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import java.util.UUID;

/**
 * Decision point tools — always available to the agent. Allows the agent to request external input
 * (approval, clarification, confirmation) during task execution.
 *
 * <p>When the agent calls {@code requestDecision}, the task transitions to {@code
 * WAITING_FOR_INPUT} status. The autonomous agent workflow detects this and pauses. External code
 * provides input via {@code Task.provideInput()}, which resumes the workflow. The decision response
 * is then included in the next iteration's user message.
 */
public class DecisionTools {

  private final ComponentClient componentClient;
  private final String taskId;

  public DecisionTools(ComponentClient componentClient, String taskId) {
    this.componentClient = componentClient;
    this.taskId = taskId;
  }

  @FunctionTool(
      description =
          "Request a decision from an external party. Use this when you need approval,"
              + " confirmation, or clarification before proceeding. Specify the question to ask and"
              + " the type of decision (approval, confirmation, or question). The task will pause"
              + " until the decision is provided. Do not call any other tools after this one.")
  public String requestDecision(String question, String decisionType) {
    var decisionId = UUID.randomUUID().toString();
    componentClient
        .forEventSourcedEntity(taskId)
        .method(TaskEntity::requestDecision)
        .invoke(new TaskEntity.DecisionRequest(decisionId, question, decisionType));

    return "DECISION_REQUESTED:"
        + decisionId
        + ". The task is now paused waiting for external input."
        + " Do not call any more tools — the next iteration will include the response.";
  }
}
