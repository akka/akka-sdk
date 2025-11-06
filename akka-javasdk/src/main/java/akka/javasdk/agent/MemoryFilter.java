/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.annotation.DoNotInherit;
import akka.javasdk.impl.agent.MemoryFiltersSupplierImpl;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Filters for controlling which messages are included when retrieving session history from memory.
 *
 * <p>Memory filters allow you to selectively include or exclude messages based on the agent
 * component id or role that produced them. This is useful in multi-agent scenarios where you want
 * to:
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
 * <p>The static factory methods return a {@link MemoryFilterSupplier} which provides a fluent
 * builder API for composing multiple filters. This supplier is designed to be used directly with
 * {@link MemoryProvider} methods.
 *
 * <p>These filters are used with {@code MemoryProvider} implementations (such as {@link
 * MemoryProvider.LimitedWindowMemoryProvider}) or directly with {@link SessionMemoryEntity}.
 *
 * @see MemoryProvider.LimitedWindowMemoryProvider for usage examples
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = MemoryFilter.Include.class, name = "include-filter"),
  @JsonSubTypes.Type(value = MemoryFilter.Exclude.class, name = "exclude-filter"),
})
public sealed interface MemoryFilter {

  /**
   * A fluent builder for composing multiple memory filters.
   *
   * <p>This interface extends {@link Supplier} returning the accumulated list of filters. It also
   * provides builder methods for chaining additional filter operations.
   *
   * <p>This supplier is designed to be used directly with {@link MemoryProvider} methods such as
   * {@link MemoryProvider.LimitedWindowMemoryProvider#readOnly(MemoryFilterSupplier)} and {@link
   * MemoryProvider.LimitedWindowMemoryProvider#filtered(MemoryFilterSupplier)}.
   *
   * <p>This is an internal API, and we do not recommend inheriting from it. To have access to an
   * implementation, use the factory methods in {@link MemoryFilter}
   *
   * @see MemoryProvider.LimitedWindowMemoryProvider for usage examples
   */
  @DoNotInherit
  interface MemoryFilterSupplier extends Supplier<List<MemoryFilter>> {

    /**
     * Adds a filter to include only messages from the specified agent component id.
     *
     * @param id the agent component id to include messages from
     * @return this supplier with the additional filter
     */
    MemoryFilterSupplier includeFromAgentId(String id);

    /**
     * Adds a filter to include only messages from the specified agent component ids.
     *
     * @param ids the set of agent component ids to include messages from
     * @return this supplier with the additional filter
     */
    MemoryFilterSupplier includeFromAgentIds(Set<String> ids);

    /**
     * Adds a filter to exclude messages from the specified agent component id.
     *
     * @param id the agent component id to exclude messages from
     * @return this supplier with the additional filter
     */
    MemoryFilterSupplier excludeFromAgentId(String id);

    /**
     * Adds a filter to exclude messages from the specified agent component ids.
     *
     * @param ids the set of agent component ids to exclude messages from
     * @return this supplier with the additional filter
     */
    MemoryFilterSupplier excludeFromAgentIds(Set<String> ids);

    /**
     * Adds a filter to include only messages from agents with the specified role.
     *
     * @param role the agent role to include messages from
     * @return this supplier with the additional filter
     */
    MemoryFilterSupplier includeFromAgentRole(String role);

    /**
     * Adds a filter to include only messages from agents with the specified roles.
     *
     * @param roles the set of agent roles to include messages from
     * @return this supplier with the additional filter
     */
    MemoryFilterSupplier includeFromAgentRoles(Set<String> roles);

    /**
     * Adds a filter to exclude messages from agents with the specified role.
     *
     * @param role the agent role to exclude messages from
     * @return this supplier with the additional filter
     */
    MemoryFilterSupplier excludeFromAgentRole(String role);

    /**
     * Adds a filter to exclude messages from agents with the specified roles.
     *
     * @param roles the set of agent roles to exclude messages from
     * @return this supplier with the additional filter
     */
    MemoryFilterSupplier excludeFromAgentRoles(Set<String> roles);
  }

  /**
   * Creates a filter supplier that includes only messages from the specified agent component id.
   *
   * <p>Returns a {@link MemoryFilterSupplier} that can be used to build additional filters via
   * method chaining, or directly passed to methods accepting a filter supplier.
   *
   * @param id the agent component id to include messages from
   * @return a filter supplier for building and composing filters
   */
  static MemoryFilterSupplier includeFromAgentId(String id) {
    return new MemoryFiltersSupplierImpl(MemoryFilter.Include.agentId(id));
  }

  /**
   * Creates a filter supplier that includes only messages from the specified agent component ids.
   *
   * <p>Returns a {@link MemoryFilterSupplier} that can be used to build additional filters via
   * method chaining, or directly passed to methods accepting a filter supplier.
   *
   * @param ids the set of agent component ids to include messages from
   * @return a filter supplier for building and composing filters
   */
  static MemoryFilterSupplier includeFromAgentIds(Set<String> ids) {
    return new MemoryFiltersSupplierImpl(MemoryFilter.Include.agentIds(ids));
  }

  /**
   * Creates a filter supplier that excludes messages from the specified agent component id.
   *
   * <p>Returns a {@link MemoryFilterSupplier} that can be used to build additional filters via
   * method chaining, or directly passed to methods accepting a filter supplier.
   *
   * @param id the agent component id to exclude messages from
   * @return a filter supplier for building and composing filters
   */
  static MemoryFilterSupplier excludeFromAgentId(String id) {
    return new MemoryFiltersSupplierImpl(MemoryFilter.Exclude.agentId(id));
  }

  /**
   * Creates a filter supplier that excludes messages from the specified agent component ids.
   *
   * <p>Returns a {@link MemoryFilterSupplier} that can be used to build additional filters via
   * method chaining, or directly passed to methods accepting a filter supplier.
   *
   * @param ids the set of agent component ids to exclude messages from
   * @return a filter supplier for building and composing filters
   */
  static MemoryFilterSupplier excludeFromAgentIds(Set<String> ids) {
    return new MemoryFiltersSupplierImpl(MemoryFilter.Exclude.agentIds(ids));
  }

  /**
   * Creates a filter supplier that includes only messages from agents with the specified role.
   *
   * <p>Returns a {@link MemoryFilterSupplier} that can be used to build additional filters via
   * method chaining, or directly passed to methods accepting a filter supplier.
   *
   * @param role the agent role to include messages from
   * @return a filter supplier for building and composing filters
   */
  static MemoryFilterSupplier includeFromAgentRole(String role) {
    return new MemoryFiltersSupplierImpl(MemoryFilter.Include.agentRole(role));
  }

  /**
   * Creates a filter supplier that includes only messages from agents with the specified roles.
   *
   * <p>Returns a {@link MemoryFilterSupplier} that can be used to build additional filters via
   * method chaining, or directly passed to methods accepting a filter supplier.
   *
   * @param roles the set of agent roles to include messages from
   * @return a filter supplier for building and composing filters
   */
  static MemoryFilterSupplier includeFromAgentRoles(Set<String> roles) {
    return new MemoryFiltersSupplierImpl(MemoryFilter.Include.agentRoles(roles));
  }

  /**
   * Creates a filter supplier that excludes messages from agents with the specified role.
   *
   * <p>Returns a {@link MemoryFilterSupplier} that can be used to build additional filters via
   * method chaining, or directly passed to methods accepting a filter supplier.
   *
   * @param role the agent role to exclude messages from
   * @return a filter supplier for building and composing filters
   */
  static MemoryFilterSupplier excludeFromAgentRole(String role) {
    return new MemoryFiltersSupplierImpl(MemoryFilter.Exclude.agentRole(role));
  }

  /**
   * Creates a filter supplier that excludes messages from agents with the specified roles.
   *
   * <p>Returns a {@link MemoryFilterSupplier} that can be used to build additional filters via
   * method chaining, or directly passed to methods accepting a filter supplier.
   *
   * @param roles the set of agent roles to exclude messages from
   * @return a filter supplier for building and composing filters
   */
  static MemoryFilterSupplier excludeFromAgentRoles(Set<String> roles) {
    return new MemoryFiltersSupplierImpl(MemoryFilter.Exclude.agentRoles(roles));
  }

  private static Set<String> union(Set<String> set1, Set<String> set2) {
    var newSet = new HashSet<String>(set1);
    newSet.addAll(set2);
    return Collections.unmodifiableSet(newSet);
  }

  /**
   * Filter that includes messages from agents with the specified component IDs or roles.
   *
   * <p>This filter uses OR logic: a message is included if either its component ID is in the {@code
   * ids} set OR its agent role is in the {@code roles} set.
   *
   * <p>When multiple Include filters are chained together using {@link MemoryFilter}, they are
   * automatically merged into a single Include filter with the union of all IDs and roles.
   *
   * <p><strong>Example:</strong> An Include filter with {@code ids={"agent-1", "agent-2"}} and
   * {@code roles={"summarizer"}} will include:
   *
   * <ul>
   *   <li>All messages from "agent-1" (regardless of role)
   *   <li>All messages from "agent-2" (regardless of role)
   *   <li>All messages with a role "summarizer" (regardless of component ID)
   * </ul>
   *
   * <p>Messages that match none of these criteria are excluded.
   *
   * @param ids the set of agent component IDs to include messages from
   * @param roles the set of agent roles to include messages from
   */
  record Include(Set<String> ids, Set<String> roles) implements MemoryFilter {

    public Include merge(Include other) {
      return new Include(union(ids, other.ids), union(roles, other.roles));
    }

    public static MemoryFilter agentId(String id) {
      return new Include(Set.of(id), Set.of());
    }

    public static MemoryFilter agentIds(Set<String> ids) {
      return new Include(ids, Set.of());
    }

    public static MemoryFilter agentRole(String role) {
      return new Include(Set.of(), Set.of(role));
    }

    public static MemoryFilter agentRoles(Set<String> roles) {
      return new Include(Set.of(), roles);
    }
  }

  /**
   * Filter that excludes messages from agents with the specified component IDs or roles.
   *
   * <p>This filter uses OR logic for exclusion: a message is excluded if either its component ID is
   * in the {@code ids} set OR its agent role is in the {@code roles} set. A message is excluded
   * only if any conditions are met:
   *
   * <p>When multiple Exclude filters are chained together using {@link MemoryFilter}, they are
   * automatically merged into a single Exclude filter with the union of all IDs and roles.
   *
   * <p><strong>Example:</strong> An Exclude filter with {@code ids={"agent-1"}} and {@code
   * roles={"worker"}} will exclude:
   *
   * <ul>
   *   <li>All messages from "agent-1" (regardless of role)
   *   <li>All messages with a role "worker" (regardless of component ID)
   * </ul>
   *
   * <p>Messages that don't match any of these exclusion criteria are included. Messages with no
   * agent role are only excluded if their component ID is in the {@code ids} set.
   *
   * @param ids the set of agent component IDs to exclude messages from
   * @param roles the set of agent roles to exclude messages from
   */
  record Exclude(Set<String> ids, Set<String> roles) implements MemoryFilter {

    public Exclude merge(Exclude other) {
      return new Exclude(union(ids, other.ids), union(roles, other.roles));
    }

    public static MemoryFilter agentId(String id) {
      return new Exclude(Set.of(id), Set.of());
    }

    public static MemoryFilter agentIds(Set<String> ids) {
      return new Exclude(ids, Set.of());
    }

    public static MemoryFilter agentRole(String role) {
      return new Exclude(Set.of(), Set.of(role));
    }

    public static MemoryFilter agentRoles(Set<String> roles) {
      return new Exclude(Set.of(), roles);
    }
  }
}
