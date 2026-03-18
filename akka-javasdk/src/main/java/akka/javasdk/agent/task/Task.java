/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import akka.javasdk.agent.MessageContent;
import java.util.ArrayList;
import java.util.List;

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
  private final List<MessageContent> attachments;
  private final List<String> policyClassNames;

  private Task(
      String description,
      Class<R> resultType,
      String instructions,
      List<MessageContent> attachments,
      List<String> policyClassNames) {
    this.description = description;
    this.resultType = resultType;
    this.instructions = instructions;
    this.attachments = attachments;
    this.policyClassNames = policyClassNames;
  }

  /**
   * Create a task definition.
   *
   * @param description what kind of work this task represents
   * @param resultType the expected result type
   */
  public static <R> Task<R> of(String description, Class<R> resultType) {
    return new Task<>(description, resultType, null, List.of(), List.of());
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
    return new Task<>(
        this.description, this.resultType, instructions, this.attachments, this.policyClassNames);
  }

  /** Content attached to this task (images, PDFs), or an empty list. */
  public List<MessageContent> attachments() {
    return attachments;
  }

  /** Return a new task with the given content attached. */
  public Task<R> attach(MessageContent content) {
    var updated = new ArrayList<>(this.attachments);
    updated.add(content);
    return new Task<>(
        this.description,
        this.resultType,
        this.instructions,
        List.copyOf(updated),
        this.policyClassNames);
  }

  /** Return a new task with the given content items attached. */
  public Task<R> attach(MessageContent... content) {
    var updated = new ArrayList<>(this.attachments);
    updated.addAll(List.of(content));
    return new Task<>(
        this.description,
        this.resultType,
        this.instructions,
        List.copyOf(updated),
        this.policyClassNames);
  }

  /** Policy class names attached to this task, or an empty list. */
  public List<String> policyClassNames() {
    return policyClassNames;
  }

  /**
   * Return a new task with the given policy attached. Policies are evaluated by the framework at
   * task lifecycle boundaries (assignment and completion).
   */
  public Task<R> policy(Class<? extends TaskPolicy<R>> policyClass) {
    var updated = new ArrayList<>(this.policyClassNames);
    updated.add(policyClass.getName());
    return new Task<>(
        this.description,
        this.resultType,
        this.instructions,
        this.attachments,
        List.copyOf(updated));
  }

  /**
   * Create a {@link TaskTemplate} from this task definition with a parameterized instruction
   * template. Template parameters use {@code {paramName}} syntax.
   *
   * <pre>{@code
   * public static final TaskTemplate<ResearchBrief> BRIEF =
   *     Task.of("Produce a research brief", ResearchBrief.class)
   *         .instructionTemplate("Research {topic}. Focus area: {focus}.");
   * }</pre>
   */
  public TaskTemplate<R> instructionTemplate(String template) {
    return new TaskTemplate<>(this.description, this.resultType, template);
  }

  @Override
  public TaskRef<R> ref(String taskId) {
    return new TaskRef<>(taskId, description, resultType);
  }
}
