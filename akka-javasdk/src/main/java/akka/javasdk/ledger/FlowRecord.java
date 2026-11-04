/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A record of a flow, as {@link LedgerClient#getFlowRecords(String)} returns it. A later SDK
 * version can add kinds of records.
 *
 * <p>Not for user extension.
 */
public sealed interface FlowRecord
    permits InteractionRecord, ActivityRecord, GuardrailDecisionRecord, EvaluationRecord {

  /** When the record was recorded. */
  Instant timestamp();

  /**
   * The records rendered as a readable transcript, for example for an LLM judge. The transcript has
   * one block for each record, in the order of the list:
   *
   * <ul>
   *   <li>An {@link InteractionRecord} starts with a line that names the agent component id, and
   *       the task name and iteration number when the interaction has a {@link TaskContext}. The
   *       lines of {@link InteractionRecord#transcript()} follow, with one difference: the {@code
   *       System:} line is written the first time a system message appears for an agent component
   *       id, and again only when it differs from the last one written for that agent component id.
   *   <li>An {@link ActivityRecord} is one line for each event. Each line names the agent component
   *       id and the instance id. A typed event writes its fields, and {@link
   *       ActivityRecord.Event.Other} writes its kind.
   *   <li>A {@link GuardrailDecisionRecord} is one line with the guardrail name, the boundary, the
   *       tool name when there is one, the decision and the explanation.
   *   <li>An {@link EvaluationRecord} is left out, so a judge does not read the verdicts of other
   *       evaluators.
   * </ul>
   *
   * <p>An empty list gives an empty string.
   *
   * @param records the records to render, such as the records of every page of a flow
   * @return the transcript of the records
   */
  static String transcript(List<? extends FlowRecord> records) {
    // the last system message written for each agent component id
    Map<String, String> systemMessages = new HashMap<>();
    var blocks = new ArrayList<String>();
    for (FlowRecord record : records) {
      var block =
          switch (record) {
            case InteractionRecord interaction -> interactionBlock(interaction, systemMessages);
            case ActivityRecord activity -> activityBlock(activity);
            case GuardrailDecisionRecord decision -> guardrailDecisionLine(decision);
            case EvaluationRecord ignored -> "";
          };
      if (!block.isEmpty()) {
        blocks.add(block);
      }
    }
    return String.join("\n", blocks);
  }

  private static String interactionBlock(
      InteractionRecord record, Map<String, String> systemMessages) {
    var systemMessage = record.systemMessage();
    var withSystemMessage =
        systemMessage != null
            && !systemMessage.isEmpty()
            && !systemMessage.equals(systemMessages.get(record.agentComponentId()));
    if (withSystemMessage) {
      systemMessages.put(record.agentComponentId(), systemMessage);
    }
    var header = new StringBuilder("Interaction of agent ").append(record.agentComponentId());
    record
        .taskContext()
        .ifPresent(
            task ->
                header
                    .append(", task ")
                    .append(task.taskName())
                    .append(", iteration ")
                    .append(task.iterationNumber()));
    return header.append(":\n").append(record.transcript(withSystemMessage)).toString();
  }

  private static String activityBlock(ActivityRecord record) {
    var prefix =
        "Activity of agent "
            + record.agentComponentId()
            + ", instance "
            + record.agentInstanceId()
            + ": ";
    var sb = new StringBuilder();
    for (ActivityRecord.Event event : record.events()) {
      sb.append(prefix).append(describe(event)).append("\n");
    }
    return sb.toString();
  }

  private static String describe(ActivityRecord.Event event) {
    return switch (event) {
      case ActivityRecord.Event.TaskAssigned e -> "task " + e.taskId() + " assigned";
      case ActivityRecord.Event.TaskStarted e ->
          "task "
              + e.taskId()
              + " started with at most "
              + iterations(e.maxIterations())
              + withText(e.taskDescription());
      case ActivityRecord.Event.TaskCompleted e ->
          "task "
              + e.taskId()
              + " completed after "
              + iterations(e.iterationsUsed())
              + withText(e.resultSummary());
      case ActivityRecord.Event.TaskFailed e ->
          "task "
              + e.taskId()
              + " failed after "
              + iterations(e.iterationsUsed())
              + withText(e.reason());
      case ActivityRecord.Event.TaskCancelled e ->
          "task "
              + e.taskId()
              + " cancelled after "
              + iterations(e.iterationsUsed())
              + withText(e.reason());
      case ActivityRecord.Event.TaskResultRejected e ->
          "result of task "
              + e.taskId()
              + " rejected after "
              + iterations(e.iterationsUsed())
              + withText(e.reason());
      case ActivityRecord.Event.IterationFailed e ->
          e.iterationNumber().map(number -> "iteration " + number).orElse("iteration")
              + e.taskId().map(taskId -> " of task " + taskId).orElse("")
              + " failed"
              + withText(e.reason());
      case ActivityRecord.Event.DelegationStarted e -> delegationStarted(e);
      case ActivityRecord.Event.DelegationResolved e ->
          "delegations resolved, "
              + e.succeeded()
              + " succeeded"
              + withList(e.succeededSubtaskIds(), " ")
              + ", "
              + e.failed()
              + " failed"
              + withList(e.failedSubtaskIds(), " ");
      case ActivityRecord.Event.HandoffStarted e ->
          "task " + e.taskId() + " handed off to " + e.targetComponentId();
      case ActivityRecord.Event.HandoffReceived e ->
          "received task "
              + e.taskId()
              + " in a handoff"
              + (e.sourceComponentId().isEmpty()
                  ? ""
                  : " from " + agent(e.sourceComponentId(), e.sourceInstanceId()));
      case ActivityRecord.Event.Other e -> "event " + e.kind();
    };
  }

  private static String delegationStarted(ActivityRecord.Event.DelegationStarted event) {
    var workers = new ArrayList<String>();
    for (int i = 0; i < event.workerComponentIds().size(); i++) {
      var instanceId = i < event.workerInstanceIds().size() ? event.workerInstanceIds().get(i) : "";
      workers.add(agent(event.workerComponentIds().get(i), instanceId));
    }
    // a delegation to a request-based agent has no subtask, and the ledger lists no worker for it
    return "started "
        + event.delegationCount()
        + (event.delegationCount() == 1 ? " delegation" : " delegations")
        + withList(workers, " to ")
        + withList(event.subtaskIds(), ", subtasks ");
  }

  private static String guardrailDecisionLine(GuardrailDecisionRecord record) {
    var sb =
        new StringBuilder("Guardrail ")
            .append(record.name())
            .append(", boundary ")
            .append(record.boundary());
    record.toolCall().ifPresent(toolCall -> sb.append(", tool ").append(toolCall.toolName()));
    return sb.append(", decision ")
        .append(record.decision())
        .append(withText(record.explanation()))
        .append("\n")
        .toString();
  }

  private static String agent(String componentId, String instanceId) {
    return instanceId.isEmpty() ? componentId : componentId + " (" + instanceId + ")";
  }

  private static String iterations(int count) {
    return count == 1 ? "1 iteration" : count + " iterations";
  }

  private static String withText(String text) {
    return text == null || text.isEmpty() ? "" : ": " + text;
  }

  private static String withList(List<String> items, String prefix) {
    return items.isEmpty() ? "" : prefix + items;
  }
}
