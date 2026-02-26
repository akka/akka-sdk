/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import java.util.ArrayList;
import java.util.List;

/** State of a task. */
public record TaskState(
    String taskId,
    String description,
    TaskStatus status,
    String assignee,
    String resultTypeName,
    String result,
    List<String> handoffContext,
    String pendingDecisionId,
    String pendingDecisionQuestion,
    String pendingDecisionType,
    String lastDecisionResponse,
    List<String> dependencyTaskIds) {

  public static TaskState empty() {
    return new TaskState(
        "", "", TaskStatus.PENDING, "", null, null, List.of(), null, null, null, null, List.of());
  }

  public TaskState withStatus(TaskStatus status) {
    return new TaskState(
        taskId,
        description,
        status,
        assignee,
        resultTypeName,
        result,
        handoffContext,
        pendingDecisionId,
        pendingDecisionQuestion,
        pendingDecisionType,
        lastDecisionResponse,
        dependencyTaskIds);
  }

  public TaskState withAssignee(String assignee) {
    return new TaskState(
        taskId,
        description,
        status,
        assignee,
        resultTypeName,
        result,
        handoffContext,
        pendingDecisionId,
        pendingDecisionQuestion,
        pendingDecisionType,
        lastDecisionResponse,
        dependencyTaskIds);
  }

  public TaskState withResult(String result) {
    return new TaskState(
        taskId,
        description,
        status,
        assignee,
        resultTypeName,
        result,
        handoffContext,
        pendingDecisionId,
        pendingDecisionQuestion,
        pendingDecisionType,
        lastDecisionResponse,
        dependencyTaskIds);
  }

  public TaskState withHandoff(String newAssignee, String context) {
    var updated = new ArrayList<>(handoffContext);
    updated.add(context);
    return new TaskState(
        taskId,
        description,
        status,
        newAssignee,
        resultTypeName,
        result,
        updated,
        pendingDecisionId,
        pendingDecisionQuestion,
        pendingDecisionType,
        lastDecisionResponse,
        dependencyTaskIds);
  }

  public TaskState withDecisionRequested(String decisionId, String question, String decisionType) {
    return new TaskState(
        taskId,
        description,
        TaskStatus.WAITING_FOR_INPUT,
        assignee,
        resultTypeName,
        result,
        handoffContext,
        decisionId,
        question,
        decisionType,
        lastDecisionResponse,
        dependencyTaskIds);
  }

  public TaskState withInputProvided(String decisionId, String response) {
    return new TaskState(
        taskId,
        description,
        TaskStatus.IN_PROGRESS,
        assignee,
        resultTypeName,
        result,
        handoffContext,
        null,
        null,
        null,
        response,
        dependencyTaskIds);
  }
}
