/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.javasdk.a2a.A2aAgentSkill;
import akka.javasdk.a2a.A2aAgents;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.a2a.A2aSkill;
import akka.javasdk.workflow.Workflow;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * INTERNAL API The skills of an A2A endpoint, each with the agent or task workflow that answers it.
 */
@InternalApi
public final class A2aSkillTable {

  /**
   * A skill and the component that answers it.
   *
   * @param agentId the component id of the agent, or of the task workflow
   * @param inputType the type of the agent's command handler parameter, or null when it has none
   * @param taskWorkflow the task workflow that answers the skill, or null for an agent
   */
  public record Entry(
      A2aAgentSkill skill,
      String agentId,
      Type inputType,
      Class<? extends Workflow<?>> taskWorkflow) {

    public Entry(A2aAgentSkill skill, String agentId, Type inputType) {
      this(skill, agentId, inputType, null);
    }
  }

  private final Map<String, Entry> entries;

  private A2aSkillTable(Map<String, Entry> entries) {
    this.entries = entries;
  }

  /**
   * @throws IllegalStateException when a class is not an agent of this service or two skills have
   *     the same id
   */
  public static A2aSkillTable of(A2aAgents agents, AgentRegistry agentRegistry) {
    var entries = new LinkedHashMap<String, Entry>();
    for (Class<?> published : agents.all()) {
      if (Workflow.class.isAssignableFrom(published)) {
        @SuppressWarnings("unchecked")
        var workflowClass = (Class<? extends Workflow<?>>) published;
        var workflowId = A2aTaskWorkflows.componentId(workflowClass);
        var component = workflowClass.getAnnotation(Component.class);
        for (A2aAgentSkill skill :
            skills(workflowClass, workflowId, component.name(), component.description())) {
          put(entries, new Entry(skill, workflowId, null, workflowClass));
        }
        continue;
      }
      @SuppressWarnings("unchecked")
      var agentClass = (Class<? extends Agent>) published;
      var component = agentClass.getAnnotation(Component.class);
      var info =
          Optional.ofNullable(component)
              .flatMap(c -> agentRegistry.agentInfoOption(c.id()))
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "["
                              + agentClass.getName()
                              + "] is published by the A2A endpoint, but it is not an agent"
                              + " component of this service"));
      var inputType = inputType(agentClass);
      for (A2aAgentSkill skill : skills(agentClass, info.id(), info.name(), info.description())) {
        put(entries, new Entry(skill, info.id(), inputType));
      }
    }
    return new A2aSkillTable(entries);
  }

  private static List<A2aAgentSkill> skills(
      Class<?> componentClass, String id, String name, String description) {
    var annotations = componentClass.getAnnotationsByType(A2aSkill.class);
    if (annotations.length == 0) {
      return List.of(
          new A2aAgentSkill(
              id,
              orElse(name, id),
              orElse(description, orElse(name, id)),
              null,
              null,
              null,
              null,
              null));
    }
    return Arrays.stream(annotations)
        .map(
            a ->
                new A2aAgentSkill(
                    a.id(),
                    orElse(a.name(), a.id()),
                    orElse(a.description(), orElse(description, orElse(name, id))),
                    List.of(a.tags()),
                    List.of(a.examples()),
                    List.of(a.inputModes()),
                    List.of(a.outputModes()),
                    null))
        .toList();
  }

  private static void put(Map<String, Entry> entries, Entry entry) {
    var previous = entries.put(entry.skill().id(), entry);
    if (previous != null) {
      throw new IllegalStateException(
          "The A2A skill id ["
              + entry.skill().id()
              + "] is used by components ["
              + previous.agentId()
              + "] and ["
              + entry.agentId()
              + "]. Skill ids must be unique.");
    }
  }

  /** True when a task workflow answers one of the skills. */
  public boolean hasTaskWorkflows() {
    return entries.values().stream().anyMatch(e -> e.taskWorkflow() != null);
  }

  /** The entry of the component with this id, for any of its skills. */
  public Optional<Entry> findByComponent(String componentId) {
    return entries.values().stream().filter(e -> e.agentId().equals(componentId)).findFirst();
  }

  public List<A2aAgentSkill> skills() {
    return entries.values().stream().map(Entry::skill).toList();
  }

  public Optional<Entry> find(String skillId) {
    return Optional.ofNullable(entries.get(skillId));
  }

  /** The first skill, which answers messages that select no skill. */
  public Optional<Entry> first() {
    return entries.values().stream().findFirst();
  }

  public List<String> ids() {
    return List.copyOf(entries.keySet());
  }

  // The agent's one command handler, as found by the component client's dynamic call.
  private static Type inputType(Class<?> agentClass) {
    for (Method method : agentClass.getMethods()) {
      boolean handler =
          (Agent.Effect.class.isAssignableFrom(method.getReturnType())
                  || Agent.StreamEffect.class.isAssignableFrom(method.getReturnType()))
              && method.getParameterCount() <= 1
              && !method.getName().startsWith("lambda$");
      if (handler) {
        return method.getParameterCount() == 0 ? null : method.getGenericParameterTypes()[0];
      }
    }
    throw new IllegalStateException(
        "Agent [" + agentClass.getName() + "] has no public command handler");
  }

  private static String orElse(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
