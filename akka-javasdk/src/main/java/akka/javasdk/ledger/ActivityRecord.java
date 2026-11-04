/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The activity events that one autonomous agent instance recorded together on a flow, as {@link
 * LedgerClient#getFlowRecords(String)} returns them.
 *
 * @param recordId the globally unique id of the record
 * @param agentComponentId the component id of the autonomous agent
 * @param agentInstanceId the id of the autonomous agent instance
 * @param flowId the id of the flow
 * @param events the events of the record, in the order they happened
 * @param timestamp when the record was recorded
 */
public record ActivityRecord(
    String recordId,
    String agentComponentId,
    String agentInstanceId,
    String flowId,
    List<ActivityRecord.Event> events,
    Instant timestamp)
    implements FlowRecord {

  /**
   * One activity event. An event of a kind that has no record of its own reads as {@link Other}. A
   * later SDK version can add records for more kinds.
   */
  public sealed interface Event {

    /** The name of the event in the stored record, such as {@code task_completed}. */
    String kind();

    /**
     * A task was assigned to the agent.
     *
     * @param taskId the id of the task
     */
    record TaskAssigned(String taskId) implements Event {
      @Override
      public String kind() {
        return "task_assigned";
      }
    }

    /**
     * The agent started to work on a task.
     *
     * @param taskId the id of the task
     * @param taskDescription the description of the task
     * @param maxIterations the maximum number of iterations for the task
     */
    record TaskStarted(String taskId, String taskDescription, int maxIterations) implements Event {
      @Override
      public String kind() {
        return "task_started";
      }
    }

    /**
     * The agent completed a task.
     *
     * @param taskId the id of the task
     * @param resultSummary a summary of the result, or an empty string when none was recorded
     * @param iterationsUsed the number of iterations the task used
     */
    record TaskCompleted(String taskId, String resultSummary, int iterationsUsed) implements Event {
      @Override
      public String kind() {
        return "task_completed";
      }
    }

    /**
     * A task ended during its execution without a result.
     *
     * @param taskId the id of the task
     * @param reason why the task failed
     * @param iterationsUsed the number of iterations the task used
     */
    record TaskFailed(String taskId, String reason, int iterationsUsed) implements Event {
      @Override
      public String kind() {
        return "task_failed";
      }
    }

    /**
     * A task was cancelled before its execution began.
     *
     * @param taskId the id of the task
     * @param reason why the task was cancelled
     * @param iterationsUsed the number of iterations the task used
     */
    record TaskCancelled(String taskId, String reason, int iterationsUsed) implements Event {
      @Override
      public String kind() {
        return "task_cancelled";
      }
    }

    /**
     * A validation rule rejected a result of a task.
     *
     * @param taskId the id of the task
     * @param reason why the result was rejected
     * @param iterationsUsed the number of iterations the task had used
     */
    record TaskResultRejected(String taskId, String reason, int iterationsUsed) implements Event {
      @Override
      public String kind() {
        return "task_result_rejected";
      }
    }

    /**
     * An iteration failed.
     *
     * @param reason why the iteration failed
     * @param taskId the id of the task, or empty for a failure outside a task
     * @param iterationNumber the number of the iteration, or empty for a failure outside a task
     */
    record IterationFailed(
        String reason, Optional<String> taskId, Optional<Integer> iterationNumber)
        implements Event {
      @Override
      public String kind() {
        return "iteration_failed";
      }
    }

    /**
     * The agent started delegations to workers. The two worker lists have one entry for each
     * delegation, in the same order. A delegation to an autonomous agent is a subtask, and {@link
     * #subtaskIds()} has its id at the same index. The runtime records each delegation to a
     * request-based agent in an event of its own, with one worker that has an empty instance id and
     * no subtask id.
     *
     * @param workerComponentIds the component ids of the workers
     * @param delegationCount the number of delegations that started
     * @param subtaskIds the ids of the subtasks, empty for a delegation to a request-based agent
     * @param workerInstanceIds the instance ids of the workers, an empty string for a request-based
     *     agent
     */
    record DelegationStarted(
        List<String> workerComponentIds,
        int delegationCount,
        List<String> subtaskIds,
        List<String> workerInstanceIds)
        implements Event {
      @Override
      public String kind() {
        return "delegation_started";
      }
    }

    /**
     * The delegations of the agent resolved. The counts include a delegation to a request-based
     * agent, and the subtask id lists leave it out.
     *
     * @param succeeded the number of delegations that succeeded
     * @param failed the number of delegations that failed
     * @param succeededSubtaskIds the ids of the subtasks that succeeded
     * @param failedSubtaskIds the ids of the subtasks that failed
     */
    record DelegationResolved(
        int succeeded, int failed, List<String> succeededSubtaskIds, List<String> failedSubtaskIds)
        implements Event {
      @Override
      public String kind() {
        return "delegation_resolved";
      }
    }

    /**
     * The agent handed off a task to another agent.
     *
     * @param taskId the id of the task
     * @param targetComponentId the component id of the agent that received the task
     */
    record HandoffStarted(String taskId, String targetComponentId) implements Event {
      @Override
      public String kind() {
        return "handoff";
      }
    }

    /**
     * The agent received a task that another agent handed off.
     *
     * @param taskId the id of the task
     * @param sourceComponentId the component id of the agent that handed off the task, or an empty
     *     string when it was not recorded
     * @param sourceInstanceId the instance id of the agent that handed off the task, or an empty
     *     string when it was not recorded
     */
    record HandoffReceived(String taskId, String sourceComponentId, String sourceInstanceId)
        implements Event {
      @Override
      public String kind() {
        return "handoff_received";
      }
    }

    /**
     * An event of a kind that has no record of its own. It carries its kind and no fields. The kind
     * is {@code unknown} for an event that the runtime cannot read.
     *
     * @param kind the name of the event in the stored record
     */
    record Other(String kind) implements Event {}
  }
}
