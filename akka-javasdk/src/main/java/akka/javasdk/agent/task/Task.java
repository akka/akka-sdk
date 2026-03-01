/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

/**
 * A typed task definition — describes a kind of work and the expected result type. Task definitions
 * are declared as {@code static final} constants and referenced everywhere:
 *
 * <pre>{@code
 * // Define tasks alongside their result types
 * public class QuestionTasks {
 *   public static final Task<Answer> ANSWER =
 *       Task.of("Answer a question", Answer.class);
 * }
 *
 * // Submit with per-request instructions
 * var ref = componentClient
 *     .forAutonomousAgent(QuestionAnswerer.class)
 *     .runSingleTask(QuestionTasks.ANSWER.instructions("What is quantum computing?"));
 *
 * // Retrieve — type comes from the definition, no class token needed
 * var task = componentClient.forTask(QuestionTasks.ANSWER.ref(id)).get();
 * Answer answer = task.result();
 * }</pre>
 *
 * <p>Immutable. {@link #instructions(String)} returns a new instance with the instructions
 * attached.
 *
 * @param <R> The result type produced when the task completes.
 */
public final class Task<R> implements TaskDef<R> {

  private final String description;
  private final Class<R> resultType;
  private final String instructions;

  private Task(String description, Class<R> resultType, String instructions) {
    this.description = description;
    this.resultType = resultType;
    this.instructions = instructions;
  }

  /**
   * Create a task definition.
   *
   * @param description what kind of work this task represents
   * @param resultType the expected result type
   */
  public static <R> Task<R> of(String description, Class<R> resultType) {
    return new Task<>(description, resultType, null);
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public Class<R> resultType() {
    return resultType;
  }

  /** Per-request instructions, or {@code null} if none were provided. */
  public String instructions() {
    return instructions;
  }

  /**
   * Return a new task with per-request instructions attached. The original task definition is
   * unchanged.
   */
  public Task<R> instructions(String instructions) {
    return new Task<>(this.description, this.resultType, instructions);
  }

  @Override
  public TaskRef<R> ref(String taskId) {
    return new TaskRef<>(taskId, description, resultType);
  }
}
