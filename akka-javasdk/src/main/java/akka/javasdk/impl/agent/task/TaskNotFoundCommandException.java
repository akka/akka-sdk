/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.task;

import akka.annotation.InternalApi;
import akka.javasdk.CommandException;

/**
 * INTERNAL API
 *
 * <p>The error reply of the task entity for a task that does not exist. The task client turns it
 * into {@link akka.javasdk.agent.task.TaskException.NotFound}, and the task operations of an
 * autonomous agent into {@code SpiTask.TaskNotFoundException}.
 */
@InternalApi
public final class TaskNotFoundCommandException extends CommandException {

  public TaskNotFoundCommandException() {
    super("Task does not exist");
  }
}
