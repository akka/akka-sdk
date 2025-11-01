/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Set;

/**
 * Filters for controlling which messages are included when retrieving session history from memory.
 *
 * <p>Memory filters allow you to selectively include or exclude messages based on the agent component id or
 * role that produced them. This is useful in multi-agent scenarios where you want to:
 *
 * <ul>
 *   <li>Retrieve only messages from specific agents (e.g., only from a "summarizer" agent)
 *   <li>Exclude messages from certain agents (e.g., exclude internal agents from user-facing
 *       history)
 *   <li>Filter by agent role to group related functionality
 * </ul>
 *
 * <p>Filters can be combined with other query parameters like {@code lastN} to retrieve the most
 * recent N messages that match the filter criteria.
 *
 * <p>These filters are used with {@code MemoryProvider} implementations (such as
 * {@link MemoryProvider.LimitedWindowMemoryProvider}) or directly with {@link SessionMemoryEntity}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = MemoryFilter.IncludeFromAgentId.class, name = "include-from-id"),
  @JsonSubTypes.Type(value = MemoryFilter.ExcludeFromAgentId.class, name = "exclude-from-id"),
  @JsonSubTypes.Type(value = MemoryFilter.IncludeFromAgentRole.class, name = "include-from-role"),
  @JsonSubTypes.Type(value = MemoryFilter.ExcludeFromAgentRole.class, name = "exclude-from-role")
})
public sealed interface MemoryFilter {
  /**
   * Filter that includes only messages from agents with the specified component ids.
   *
   * <p>When applied, only messages where the component id matches one of the component ids in this filter
   * will be included in the result. All other messages will be excluded.
   *
   * @param ids the set of agent component ids to include
   */
  record IncludeFromAgentId(Set<String> ids) implements MemoryFilter {}

  /**
   * Filter that excludes messages from agents with the specified component ids.
   *
   * <p>When applied, messages where the component id matches one of the component ids in this filter will be
   * excluded from the result. All other messages will be included.
   *
   * @param ids the set of agent component ids to exclude
   */
  record ExcludeFromAgentId(Set<String> ids) implements MemoryFilter {}

  /**
   * Filter that includes only messages from agents with the specified roles.
   *
   * <p>When applied, only messages where the agent role matches one of the roles in this filter
   * will be included in the result. All other messages will be excluded.
   *
   * @param roles the set of agent roles to include
   */
  record IncludeFromAgentRole(Set<String> roles) implements MemoryFilter {}

  /**
   * Filter that excludes messages from agents with the specified roles.
   *
   * <p>When applied, messages where the agent role matches one of the roles in this filter will be
   * excluded from the result. All other messages will be included.
   *
   * @param roles the set of agent roles to exclude
   */
  record ExcludeFromAgentRole(Set<String> roles) implements MemoryFilter {}

  /**
   * Creates a filter that includes only messages from the specified agent component id.
   *
   * @param id the agent component id to include messages from
   * @return a filter that includes only messages from the specified agent
   */
  static MemoryFilter includeFromAgentId(String id) {
    return new IncludeFromAgentId(Set.of(id));
  }

  /**
   * Creates a filter that includes only messages from the specified agent component ids.
   *
   * @param ids the set of agent component ids to include messages from
   * @return a filter that includes only messages from the specified agents
   */
  static MemoryFilter includeFromAgentId(Set<String> ids) {
    return new IncludeFromAgentId(ids);
  }

  /**
   * Creates a filter that excludes messages from the specified agent component id.
   *
   * @param id the agent component id to exclude messages from
   * @return a filter that excludes messages from the specified agent
   */
  static MemoryFilter excludeFromAgentId(String id) {
    return new ExcludeFromAgentId(Set.of(id));
  }

  /**
   * Creates a filter that excludes messages from the specified agent component ids.
   *
   * @param ids the set of agent component ids to exclude messages from
   * @return a filter that excludes messages from the specified agents
   */
  static MemoryFilter excludeFromAgentId(Set<String> ids) {
    return new ExcludeFromAgentId(ids);
  }

  /**
   * Creates a filter that includes only messages from agents with the specified role.
   *
   * @param role the agent role to include messages from
   * @return a filter that includes only messages from agents with the specified role
   */
  static MemoryFilter includeFromAgentRole(String role) {
    return new IncludeFromAgentRole(Set.of(role));
  }

  /**
   * Creates a filter that includes only messages from agents with the specified roles.
   *
   * @param roles the set of agent roles to include messages from
   * @return a filter that includes only messages from agents with the specified roles
   */
  static MemoryFilter includeFromAgentRole(Set<String> roles) {
    return new IncludeFromAgentRole(roles);
  }

  /**
   * Creates a filter that excludes messages from agents with the specified role.
   *
   * @param role the agent role to exclude messages from
   * @return a filter that excludes messages from agents with the specified role
   */
  static MemoryFilter excludeFromAgentRole(String role) {
    return new ExcludeFromAgentRole(Set.of(role));
  }

  /**
   * Creates a filter that excludes messages from agents with the specified roles.
   *
   * @param roles the set of agent roles to exclude messages from
   * @return a filter that excludes messages from agents with the specified roles
   */
  static MemoryFilter excludeFromAgentRole(Set<String> roles) {
    return new ExcludeFromAgentRole(roles);
  }
}
