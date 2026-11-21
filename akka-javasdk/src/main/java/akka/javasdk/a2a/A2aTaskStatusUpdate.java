/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * An event that reports a new status of a task.
 *
 * <p>A workflow that follows a remote task with {@link
 * RemoteA2aAgentClient.SendRequest#notifyWorkflow} receives one more status update when the
 * following ends before the task does, after {@code follow-timeout}. The SDK creates that update,
 * not the remote agent: {@link #followTimedOut()} returns true, its state is {@link
 * A2aTaskState#UNSPECIFIED}, and the remote task may still be running.
 *
 * @param taskId the id of the task
 * @param contextId the id of the conversation that the task belongs to
 * @param status the new status
 * @param metadata free form details about the event
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aTaskStatusUpdate(
    String taskId, String contextId, A2aTaskStatus status, Map<String, Object> metadata)
    implements A2aTaskUpdate {

  /** The metadata key that marks the update the SDK sends when following a remote task ends. */
  public static final String FOLLOW_TIMED_OUT = "akka.followTimedOut";

  public A2aTaskStatusUpdate {
    metadata = A2aValues.optionalMap(metadata);
  }

  /**
   * True when the SDK stopped following the remote task after {@code follow-timeout}, before the
   * task ended or asked for input. No more updates of the task are delivered.
   */
  public boolean followTimedOut() {
    return metadata != null && Boolean.TRUE.equals(metadata.get(FOLLOW_TIMED_OUT));
  }
}
