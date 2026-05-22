/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

/**
 * A rule that validates the result of a task before accepting its completion.
 *
 * <p>Add rules to a {@link Task} definition with {@link Task#rules}. When the task is completed,
 * each rule's {@link #onComplete} method is called with the deserialized result. If any rule
 * returns {@link Result.Rejected}, the task is failed instead of completed.
 *
 * <p>Implementations must have a public no-arg constructor.
 *
 * @param <R> The result type of the task.
 */
public interface TaskRule<R> {

  /**
   * Evaluate the task result before accepting completion.
   *
   * @param result the deserialized task result
   * @return {@link Result.Accepted} to allow completion, or {@link Result.Rejected} to reject it
   */
  Result onComplete(R result);

  sealed interface Result {
    record Accepted() implements Result {}

    record Rejected(String reason) implements Result {}
  }
}
