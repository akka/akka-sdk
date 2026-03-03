/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.Guardrail;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.RemoteMcpTools;
import akka.javasdk.agent.task.TaskDefinition;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable implementation of {@link AutonomousStrategy}. Each fluent method returns a new
 * instance.
 *
 * @hidden
 */
public final class DefaultAutonomousStrategy implements AutonomousStrategy {

  private final String goal;
  private final List<TaskDefinition<?>> acceptedTasks;
  private final ModelProvider modelProvider;
  private final List<Object> toolInstancesOrClasses;
  private final List<RemoteMcpTools> mcpTools;
  private final List<String> requestGuardrailClassNames;
  private final List<String> responseGuardrailClassNames;
  private final MemoryProvider memoryProvider;
  private final int maxIterations;

  DefaultAutonomousStrategy() {
    this(
        "",
        List.of(),
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        MemoryProvider.fromConfig(),
        DEFAULT_MAX_ITERATIONS);
  }

  private DefaultAutonomousStrategy(
      String goal,
      List<TaskDefinition<?>> acceptedTasks,
      ModelProvider modelProvider,
      List<Object> toolInstancesOrClasses,
      List<RemoteMcpTools> mcpTools,
      List<String> requestGuardrailClassNames,
      List<String> responseGuardrailClassNames,
      MemoryProvider memoryProvider,
      int maxIterations) {
    this.goal = goal;
    this.acceptedTasks = acceptedTasks;
    this.modelProvider = modelProvider;
    this.toolInstancesOrClasses = toolInstancesOrClasses;
    this.mcpTools = mcpTools;
    this.requestGuardrailClassNames = requestGuardrailClassNames;
    this.responseGuardrailClassNames = responseGuardrailClassNames;
    this.memoryProvider = memoryProvider;
    this.maxIterations = maxIterations;
  }

  @Override
  public AutonomousStrategy goal(String goal) {
    return new DefaultAutonomousStrategy(
        goal,
        acceptedTasks,
        modelProvider,
        toolInstancesOrClasses,
        mcpTools,
        requestGuardrailClassNames,
        responseGuardrailClassNames,
        memoryProvider,
        maxIterations);
  }

  @Override
  public AutonomousStrategy accepts(TaskDefinition<?>... tasks) {
    var updated = new ArrayList<>(this.acceptedTasks);
    updated.addAll(List.of(tasks));
    return new DefaultAutonomousStrategy(
        goal,
        List.copyOf(updated),
        modelProvider,
        toolInstancesOrClasses,
        mcpTools,
        requestGuardrailClassNames,
        responseGuardrailClassNames,
        memoryProvider,
        maxIterations);
  }

  @Override
  public AutonomousStrategy modelProvider(ModelProvider provider) {
    return new DefaultAutonomousStrategy(
        goal,
        acceptedTasks,
        provider,
        toolInstancesOrClasses,
        mcpTools,
        requestGuardrailClassNames,
        responseGuardrailClassNames,
        memoryProvider,
        maxIterations);
  }

  @Override
  public AutonomousStrategy tools(Object... toolInstancesOrClasses) {
    var updated = new ArrayList<>(this.toolInstancesOrClasses);
    updated.addAll(List.of(toolInstancesOrClasses));
    return new DefaultAutonomousStrategy(
        goal,
        acceptedTasks,
        modelProvider,
        List.copyOf(updated),
        mcpTools,
        requestGuardrailClassNames,
        responseGuardrailClassNames,
        memoryProvider,
        maxIterations);
  }

  @Override
  public AutonomousStrategy mcpTools(RemoteMcpTools... mcpTools) {
    var updated = new ArrayList<>(this.mcpTools);
    updated.addAll(List.of(mcpTools));
    return new DefaultAutonomousStrategy(
        goal,
        acceptedTasks,
        modelProvider,
        toolInstancesOrClasses,
        List.copyOf(updated),
        requestGuardrailClassNames,
        responseGuardrailClassNames,
        memoryProvider,
        maxIterations);
  }

  @Override
  @SafeVarargs
  public final AutonomousStrategy requestGuardrails(Class<? extends Guardrail>... guardrails) {
    var updated = new ArrayList<>(this.requestGuardrailClassNames);
    for (Class<? extends Guardrail> clz : guardrails) {
      updated.add(clz.getName());
    }
    return new DefaultAutonomousStrategy(
        goal,
        acceptedTasks,
        modelProvider,
        toolInstancesOrClasses,
        mcpTools,
        List.copyOf(updated),
        responseGuardrailClassNames,
        memoryProvider,
        maxIterations);
  }

  @Override
  @SafeVarargs
  public final AutonomousStrategy responseGuardrails(Class<? extends Guardrail>... guardrails) {
    var updated = new ArrayList<>(this.responseGuardrailClassNames);
    for (Class<? extends Guardrail> clz : guardrails) {
      updated.add(clz.getName());
    }
    return new DefaultAutonomousStrategy(
        goal,
        acceptedTasks,
        modelProvider,
        toolInstancesOrClasses,
        mcpTools,
        requestGuardrailClassNames,
        List.copyOf(updated),
        memoryProvider,
        maxIterations);
  }

  @Override
  public AutonomousStrategy memory(MemoryProvider memory) {
    return new DefaultAutonomousStrategy(
        goal,
        acceptedTasks,
        modelProvider,
        toolInstancesOrClasses,
        mcpTools,
        requestGuardrailClassNames,
        responseGuardrailClassNames,
        memory,
        maxIterations);
  }

  @Override
  public AutonomousStrategy maxIterations(int max) {
    return new DefaultAutonomousStrategy(
        goal,
        acceptedTasks,
        modelProvider,
        toolInstancesOrClasses,
        mcpTools,
        requestGuardrailClassNames,
        responseGuardrailClassNames,
        memoryProvider,
        max);
  }

  // Data accessors for framework use

  public String goal() {
    return goal;
  }

  public List<TaskDefinition<?>> acceptedTasks() {
    return acceptedTasks;
  }

  public ModelProvider modelProvider() {
    return modelProvider;
  }

  public List<Object> toolInstancesOrClasses() {
    return toolInstancesOrClasses;
  }

  public List<RemoteMcpTools> mcpTools() {
    return mcpTools;
  }

  public List<String> requestGuardrailClassNames() {
    return requestGuardrailClassNames;
  }

  public List<String> responseGuardrailClassNames() {
    return responseGuardrailClassNames;
  }

  public MemoryProvider memoryProvider() {
    return memoryProvider;
  }

  public int maxIterations() {
    return maxIterations;
  }
}
