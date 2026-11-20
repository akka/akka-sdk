/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * An event that reports a new status of a task.
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

  public A2aTaskStatusUpdate {
    metadata = A2aValues.optionalMap(metadata);
  }
}
