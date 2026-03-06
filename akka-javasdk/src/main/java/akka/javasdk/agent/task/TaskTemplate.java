/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A task definition with a parameterized instruction template. Template parameters use {@code
 * {paramName}} syntax and are resolved via {@link #params(Map)} to produce a submittable {@link
 * Task}.
 *
 * <p>Use {@link #define(String)} to create a template, then {@link #resultConformsTo(Class)} to set
 * the result type and {@link #instructionTemplate(String)} to set the template string. Templates
 * are immutable and typically declared as {@code static final} constants.
 *
 * <p>At request time, resolve the template with {@link #params(Map)} or override it entirely with
 * {@link #instructions(String)}.
 *
 * @param <R> The result type produced when the task completes.
 */
public final class TaskTemplate<R> implements TaskDefinition<R> {

  private static final Pattern PARAM_PATTERN = Pattern.compile("\\{(\\w+)}");

  private final String name;
  private final String description;
  private final Class<R> resultType;
  private final String instructionTemplate;

  TaskTemplate(String name, String description, Class<R> resultType, String instructionTemplate) {
    this.name = name;
    this.description = description;
    this.resultType = resultType;
    this.instructionTemplate = instructionTemplate;
  }

  /**
   * Define a new task template with the given name. The name is a stable identifier for this task
   * type. The default result type is {@code String}; call {@link #resultConformsTo(Class)} to
   * change it.
   *
   * @param name a stable identifier for this task type
   */
  public static TaskTemplate<String> define(String name) {
    return new TaskTemplate<>(name, "", String.class, "");
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
  public TaskTemplate<R> description(String description) {
    return new TaskTemplate<>(this.name, description, this.resultType, this.instructionTemplate);
  }

  @Override
  public Class<R> resultType() {
    return resultType;
  }

  /**
   * Set the expected result type. Returns a new task template with the changed type parameter.
   *
   * @param <S> The new result type
   */
  public <S> TaskTemplate<S> resultConformsTo(Class<S> type) {
    return new TaskTemplate<>(this.name, this.description, type, this.instructionTemplate);
  }

  /** Set the instruction template with {@code {paramName}} placeholders. */
  public TaskTemplate<R> instructionTemplate(String template) {
    return new TaskTemplate<>(this.name, this.description, this.resultType, template);
  }

  /** The instruction template string with {@code {paramName}} placeholders. */
  public String instructionTemplate() {
    return instructionTemplate;
  }

  /** Extract parameter names from the template. */
  public List<String> templateParameterNames() {
    var names = new ArrayList<String>();
    var matcher = PARAM_PATTERN.matcher(instructionTemplate);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }

  /**
   * Resolve the template with named parameters, returning a submittable {@link Task}.
   *
   * @param templateParams parameter values keyed by name
   * @throws IllegalArgumentException if a template parameter has no value in the map
   */
  public Task<R> params(Map<String, String> templateParams) {
    var resolved = instructionTemplate;
    var matcher = PARAM_PATTERN.matcher(instructionTemplate);
    while (matcher.find()) {
      var paramName = matcher.group(1);
      var value = templateParams.get(paramName);
      if (value == null) {
        throw new IllegalArgumentException(
            "Missing template parameter '" + paramName + "' for task '" + description + "'");
      }
      resolved = resolved.replace("{" + paramName + "}", value);
    }
    return new Task<>(name, description, resultType, resolved, List.of(), List.of());
  }

  /** Override the template with free-form instructions, returning a submittable {@link Task}. */
  public Task<R> instructions(String instructions) {
    return new Task<>(name, description, resultType, instructions, List.of(), List.of());
  }
}
