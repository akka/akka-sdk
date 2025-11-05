/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.annotation.DoNotInherit;
import akka.javasdk.impl.agent.MemoryFiltersSupplierImpl;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
  @JsonSubTypes.Type(value = MemoryFilter.IncludeFromAgentId.class, name = "include-from-id"),
  @JsonSubTypes.Type(value = MemoryFilter.ExcludeFromAgentId.class, name = "exclude-from-id"),
  @JsonSubTypes.Type(value = MemoryFilter.IncludeFromAgentRole.class, name = "include-from-role"),
  @JsonSubTypes.Type(value = MemoryFilter.ExcludeFromAgentRole.class, name = "exclude-from-role")
})
public sealed interface MemoryFilter {

  /**
   * A fluent builder for composing multiple memory filters.
   *
   * <p>This interface extends {@link Supplier} returning the accumulated list of filters. It also
   * provides builder methods for chaining additional filter operations.
   *
   * <p>This supplier is designed to be used directly with {@link MemoryProvider} methods such as
   * {@link MemoryProvider.LimitedWindowMemoryProvider#readOnly(Supplier)} and {@link
   * MemoryProvider.LimitedWindowMemoryProvider#filtered(Supplier)}.
   *
   * This is an internal API, and we do not recommend inheriting from it. To have access to an implementation, use
   * the factory methods in {@link MemoryFilter}
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
    MemoryFilterSupplier includeFromAgentId(Set<String> ids);

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
    MemoryFilterSupplier excludeFromAgentId(Set<String> ids);

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
    MemoryFilterSupplier includeFromAgentRole(Set<String> roles);

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
    MemoryFilterSupplier excludeFromAgentRole(Set<String> roles);
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
    return new MemoryFiltersSupplierImpl(new MemoryFilter.IncludeFromAgentId(Set.of(id)));
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
  static MemoryFilterSupplier includeFromAgentId(Set<String> ids) {
    return new MemoryFiltersSupplierImpl(new MemoryFilter.IncludeFromAgentId(ids));
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
    return new MemoryFiltersSupplierImpl(new MemoryFilter.ExcludeFromAgentId(Set.of(id)));
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
  static MemoryFilterSupplier excludeFromAgentId(Set<String> ids) {
    return new MemoryFiltersSupplierImpl(new MemoryFilter.ExcludeFromAgentId(ids));
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
    return new MemoryFiltersSupplierImpl(new MemoryFilter.IncludeFromAgentRole(Set.of(role)));
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
  static MemoryFilterSupplier includeFromAgentRole(Set<String> roles) {
    return new MemoryFiltersSupplierImpl(new MemoryFilter.IncludeFromAgentRole(roles));
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
    return new MemoryFiltersSupplierImpl(new MemoryFilter.ExcludeFromAgentRole(Set.of(role)));
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
  static MemoryFilterSupplier excludeFromAgentRole(Set<String> roles) {
    return new MemoryFiltersSupplierImpl(new MemoryFilter.ExcludeFromAgentRole(roles));
  }

  /**
   * Filter that includes only messages from agents with the specified component ids.
   *
   * <p>When applied, only messages where the component id matches one of the component ids in this
   * filter will be included in the result. All other messages will be excluded.
   *
   * @param ids the set of agent component ids to include
   */
  record IncludeFromAgentId(Set<String> ids) implements MemoryFilter {}

  /**
   * Filter that excludes messages from agents with the specified component ids.
   *
   * <p>When applied, messages where the component id matches one of the component ids in this
   * filter will be excluded from the result. All other messages will be included.
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
}
