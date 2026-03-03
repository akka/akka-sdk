/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import akka.javasdk.agent.MessageContent;
import java.util.ArrayList;
import java.util.List;

/**
 * A typed task definition — describes a kind of work and the expected result type.
 *
 * <p>Use {@link #define(String)} to create a definition, then {@link #resultConformsTo(Class)} to
 * set the result type. Task definitions are immutable and typically declared as {@code static
 * final} constants. The default result type is {@code String}.
 *
 * <p>Per-request methods like {@link #instructions(String)}, {@link #attach(MessageContent...)},
 * and {@link #dependsOn(String...)} return a new instance, leaving the original definition
 * unchanged. This allows a single definition to be reused across many requests.
 *
 * @param <R> The result type produced when the task completes.
 */
public final class Task<R> implements TaskDefinition<R> {

  private final String name;
  private final String description;
  private final Class<R> resultType;
  private final String instructions;
  private final List<MessageContent> attachments;
  private final List<String> dependencyTaskIds;

  Task(
      String name,
      String description,
      Class<R> resultType,
      String instructions,
      List<MessageContent> attachments,
      List<String> dependencyTaskIds) {
    this.name = name;
    this.description = description;
    this.resultType = resultType;
    this.instructions = instructions;
    this.attachments = attachments;
    this.dependencyTaskIds = dependencyTaskIds;
  }

  /**
   * Define a new task with the given name. The name is a stable identifier for this task type. The
   * default result type is {@code String}; call {@link #resultConformsTo(Class)} to change it.
   *
   * @param name a stable identifier for this task type
   */
  public static Task<String> define(String name) {
    return new Task<>(name, "", String.class, "", List.of(), List.of());
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String description() {
    return description;
  }

  /** Set the description of this task — what kind of work it represents. */
  public Task<R> description(String description) {
    return new Task<>(
        this.name,
        description,
        this.resultType,
        this.instructions,
        this.attachments,
        this.dependencyTaskIds);
  }

  @Override
  public Class<R> resultType() {
    return resultType;
  }

  /** Per-request instructions, or empty string if none were provided. */
  public String instructions() {
    return instructions;
  }

  /**
   * Return a new task with per-request instructions attached. The original task definition is
   * unchanged.
   */
  public Task<R> instructions(String instructions) {
    return new Task<>(
        this.name,
        this.description,
        this.resultType,
        instructions,
        this.attachments,
        this.dependencyTaskIds);
  }

  /** Content attached to this task (images, PDFs), or an empty list. */
  public List<MessageContent> attachments() {
    return attachments;
  }

  /** Return a new task with the given content items attached. */
  public Task<R> attach(MessageContent... content) {
    var updated = new ArrayList<>(this.attachments);
    updated.addAll(List.of(content));
    return new Task<>(
        this.name,
        this.description,
        this.resultType,
        this.instructions,
        List.copyOf(updated),
        this.dependencyTaskIds);
  }

  /**
   * Set the expected result type. Returns a new task with the changed type parameter.
   *
   * @param <S> The new result type
   */
  public <S> Task<S> resultConformsTo(Class<S> type) {
    return new Task<>(
        this.name,
        this.description,
        type,
        this.instructions,
        this.attachments,
        this.dependencyTaskIds);
  }

  /** Task IDs that must complete before this task can start, or an empty list. */
  public List<String> dependencyTaskIds() {
    return dependencyTaskIds;
  }

  /**
   * Return a new task that depends on the given task IDs. The agent will not start this task until
   * all dependencies are completed.
   */
  public Task<R> dependsOn(String... taskIds) {
    var updated = new ArrayList<>(this.dependencyTaskIds);
    updated.addAll(List.of(taskIds));
    return new Task<>(
        this.name,
        this.description,
        this.resultType,
        this.instructions,
        this.attachments,
        List.copyOf(updated));
  }
}
