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
 * {paramName}} syntax and must be resolved before submission.
 *
 * <p>A {@code TaskTemplate} cannot be submitted directly — {@link
 * akka.javasdk.client.AutonomousAgentClient#runSingleTask} requires {@link Task}, not {@link
 * TaskDef}. Resolve the template first:
 *
 * <pre>{@code
 * // Resolve with named params
 * var task = ResearchTasks.BRIEF.params(Map.of("topic", "quantum computing", "focus", "crypto"));
 *
 * // Or override with free-form instructions
 * var task = ResearchTasks.BRIEF.instructions("Research quantum computing");
 * }</pre>
 *
 * @param <R> The result type produced when the task completes.
 */
public final class TaskTemplate<R> implements TaskDef<R> {

  private static final Pattern PARAM_PATTERN = Pattern.compile("\\{(\\w+)}");

  private final String description;
  private final Class<R> resultType;
  private final String instructionTemplate;

  TaskTemplate(String description, Class<R> resultType, String instructionTemplate) {
    this.description = description;
    this.resultType = resultType;
    this.instructionTemplate = instructionTemplate;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public Class<R> resultType() {
    return resultType;
  }

  /** The instruction template string with {@code {paramName}} placeholders. */
  public String instructionTemplate() {
    return instructionTemplate;
  }

  /**
   * Extract parameter names from the template. For example, {@code "Research {topic}. Focus:
   * {focus}."} returns {@code ["topic", "focus"]}.
   */
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
    return Task.of(description, resultType).instructions(resolved);
  }

  /**
   * Override the template with free-form instructions, returning a submittable {@link Task}. Use
   * this when the template doesn't fit the request.
   */
  public Task<R> instructions(String instructions) {
    return Task.of(description, resultType).instructions(instructions);
  }

  @Override
  public TaskRef<R> ref(String taskId) {
    return new TaskRef<>(taskId, description, resultType);
  }
}
