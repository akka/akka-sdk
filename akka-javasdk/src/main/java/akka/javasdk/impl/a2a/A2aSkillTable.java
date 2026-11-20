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
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** INTERNAL API The skills of an A2A endpoint, each with the agent that answers it. */
@InternalApi
public final class A2aSkillTable {

  /**
   * A skill and its agent.
   *
   * @param inputType the type of the agent's command handler parameter, or null when it has none
   */
  public record Entry(A2aAgentSkill skill, String agentId, Type inputType) {}

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
    for (Class<? extends Agent> agentClass : agents.agents()) {
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
      var annotations = agentClass.getAnnotationsByType(A2aSkill.class);
      List<A2aAgentSkill> skills =
          annotations.length == 0
              ? List.of(
                  new A2aAgentSkill(
                      info.id(),
                      orElse(info.name(), info.id()),
                      orElse(info.description(), orElse(info.name(), info.id())),
                      null,
                      null,
                      null,
                      null,
                      null))
              : Arrays.stream(annotations)
                  .map(
                      a ->
                          new A2aAgentSkill(
                              a.id(),
                              orElse(a.name(), a.id()),
                              orElse(
                                  a.description(),
                                  orElse(info.description(), orElse(info.name(), info.id()))),
                              List.of(a.tags()),
                              List.of(a.examples()),
                              List.of(a.inputModes()),
                              List.of(a.outputModes()),
                              null))
                  .toList();
      for (A2aAgentSkill skill : skills) {
        var previous = entries.put(skill.id(), new Entry(skill, info.id(), inputType));
        if (previous != null) {
          throw new IllegalStateException(
              "The A2A skill id ["
                  + skill.id()
                  + "] is used by agents ["
                  + previous.agentId()
                  + "] and ["
                  + info.id()
                  + "]. Skill ids must be unique.");
        }
      }
    }
    return new A2aSkillTable(entries);
  }

  public List<A2aAgentSkill> skills() {
    return entries.values().stream().map(Entry::skill).toList();
  }

  public Optional<Entry> find(String skillId) {
    return Optional.ofNullable(entries.get(skillId));
  }

  /** The only skill, when the endpoint publishes exactly one. */
  public Optional<Entry> single() {
    return entries.size() == 1 ? entries.values().stream().findFirst() : Optional.empty();
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
