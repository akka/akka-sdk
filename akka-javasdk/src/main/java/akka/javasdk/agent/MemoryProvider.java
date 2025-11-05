/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.List;
import java.util.Optional;

/**
 * Interface for configuring memory management in agent systems.
 *
 * <p>MemoryProvider defines how session history is stored and retrieved during agent interactions.
 * It offers several implementation strategies:
 *
 * <ul>
 *   <li>Limited window memory management via {@link LimitedWindowMemoryProvider}
 *   <li>Custom memory implementation via {@link CustomMemoryProvider}
 * </ul>
 */
public sealed interface MemoryProvider {

  /**
   * Creates a configuration-based memory provider based on configuration defaults.
   *
   * @return A configuration-based memory provider
   */
  static MemoryProvider fromConfig() {
    return fromConfig("");
  }

  /**
   * Creates a memory provider based on configuration settings.
   *
   * @param configPath Path to the configuration. If empty, uses the default path
   *     "akka.javasdk.agent.memory"
   * @return A configuration-based memory provider
   */
  static MemoryProvider fromConfig(String configPath) {
    return new MemoryProvider.FromConfig(configPath);
  }

  /** Configuration-based memory provider that reads settings from the specified path. */
  record FromConfig(String configPath) implements MemoryProvider {}

  /** Disabled memory provider, which does not store or retrieve contextual history. */
  record Disabled() implements MemoryProvider {}

  /**
   * Disabled memory provider, which does not store or retrieve contextual history.
   *
   * @return A memory provider without memory.
   */
  static Disabled none() {
    return new Disabled();
  }

  /**
   * Creates a limited window memory provider with default settings.
   *
   * <p>The default settings are:
   *
   * <ul>
   *   <li>Include all session history in each interaction with the model
   *   <li>Record all interactions into memory
   * </ul>
   *
   * @return A new limited window memory provider with default settings
   */
  static LimitedWindowMemoryProvider limitedWindow() {
    return new LimitedWindowMemoryProvider(Optional.empty(), true, true, List.of());
  }

  /**
   * Memory provider that limits session history based on size or message count.
   *
   * <p>This provider allows fine-grained control over memory usage by limiting:
   *
   * <ul>
   *   <li>Use only last N messages from the history
   *   <li>Whether reading from memory is enabled
   *   <li>Whether writing to memory is enabled
   *   <li>Applies memory filters {@link MemoryFilter}
   * </ul>
   *
   * <p><strong>Filter Ordering:</strong> When multiple filters are specified, filters of the same
   * type are automatically merged. The merged filters are then applied in the order that each
   * filter type first appears. Each filter type operates on the result of the previous filter type.
   *
   * <p>Example usage with filters:
   *
   * <pre>{@code
   * // Single filter - include only messages from a specific agent
   * MemoryProvider.limitedWindow()
   *     .readOnly(MemoryFilter.includeFromAgentId("agent-1"));
   *
   * // Multiple filters - include messages from agent-1 but exclude internal role
   * // includeFromAgentId is applied first, then excludeFromAgentRole
   * MemoryProvider.limitedWindow()
   *     .readOnly(MemoryFilter.includeFromAgentId("agent-1")
   *                           .excludeFromAgentRole("internal"));
   *
   * // Combined with readLast - last 10 messages from specific agents
   * // The two includeFromAgentId calls are merged, then filters applied, then limit to last 10
   * MemoryProvider.limitedWindow()
   *     .readLast(10, MemoryFilter.includeFromAgentId("agent-1")
   *                               .includeFromAgentId("agent-2"));
   *
   * // Using filtered() for read-write with filters
   * // The two excludeFromAgentRole calls are merged into a single filter
   * MemoryProvider.limitedWindow()
   *     .filtered(MemoryFilter.excludeFromAgentRole("internal")
   *                           .excludeFromAgentRole("debug"));
   * }</pre>
   */
  record LimitedWindowMemoryProvider(
      Optional<Integer> readLastN, boolean read, boolean write, List<MemoryFilter> filters)
      implements MemoryProvider {

    /**
     * Creates a read-only version of this memory provider.
     *
     * <p>The returned provider will allow reading from memory but disable writing.
     *
     * @return A new memory provider with writing disabled
     */
    public MemoryProvider readOnly() {
      return new LimitedWindowMemoryProvider(readLastN, true, false, List.of());
    }

    /**
     * Creates a read-only version of this memory provider with multiple filters applied.
     *
     * <p>The returned provider will allow reading from memory but disable writing. The specified
     * filters control which messages are included when reading from memory.
     *
     * <p>Filters of the same type are automatically merged. The merged filters are then applied in
     * the order that each filter type first appears. Each filter type operates on the result of the
     * previous filter type.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * // Single filter
     * MemoryProvider.limitedWindow()
     *     .readOnly(MemoryFilter.includeFromAgentId("agent-1"));
     *
     * // Multiple chained filters - same types are merged
     * MemoryProvider.limitedWindow()
     *     .readOnly(MemoryFilter.includeFromAgentId("agent-1")
     *                           .excludeFromAgentRole("internal"));
     * }</pre>
     *
     * @param filtersSupplier a supplier that provides the list of filters to apply
     * @return A new memory provider with writing disabled and the specified filters
     */
    public MemoryProvider readOnly(MemoryFilter.MemoryFilterSupplier filtersSupplier) {
      return new LimitedWindowMemoryProvider(readLastN, true, false, filtersSupplier.get());
    }

    /**
     * Creates a write-only version of this memory provider.
     *
     * <p>The returned provider will allow writing to memory but disable reading.
     *
     * @return A new memory provider with reading disabled
     */
    public MemoryProvider writeOnly() {
      return new LimitedWindowMemoryProvider(readLastN, false, true, List.of());
    }

    /**
     * Creates a new memory provider with an updated history limit.
     *
     * <p>The history limit controls the maximum number of messages to retain in memory.
     *
     * @param onlyLastN parameter controls the maximum number of most recent messages to read from
     *     memory.
     * @return A new memory provider with the specified history limit
     */
    public MemoryProvider readLast(int onlyLastN) {
      return new LimitedWindowMemoryProvider(Optional.of(onlyLastN), read, write, List.of());
    }

    /**
     * Creates a new memory provider with an updated history limit and multiple filters applied.
     *
     * <p>The history limit controls the maximum number of messages to read from memory. Filters of
     * the same type are automatically merged, then applied in the order that each filter type first
     * appears, and finally, the limit is enforced on the filtered results.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * // Last 10 messages from a specific agent
     * MemoryProvider.limitedWindow()
     *     .readLast(10, MemoryFilter.includeFromAgentId("agent-1"));
     *
     * // Last 5 messages excluding internal agents
     * // Both excludeFromAgentRole calls are merged, applied, then limit to last 5
     * MemoryProvider.limitedWindow()
     *     .readLast(5, MemoryFilter.excludeFromAgentRole("internal")
     *                              .excludeFromAgentRole("debug"));
     * }</pre>
     *
     * @param onlyLastN the maximum number of most recent messages to read from memory
     * @param filtersSupplier a supplier that provides the list of filters to apply
     * @return A new memory provider with the specified history limit and filters
     */
    public MemoryProvider readLast(int onlyLastN, MemoryFilter.MemoryFilterSupplier filtersSupplier) {
      return new LimitedWindowMemoryProvider(Optional.of(onlyLastN), read, write, filtersSupplier.get());
    }

    /**
     * Creates a new memory provider with multiple filters applied.
     *
     * <p>The specified filters control which messages are included when reading from memory.
     *
     * <p>Filters of the same type are automatically merged. The merged filters are then applied in
     * the order that each filter type first appears. Each filter type operates on the result of the
     * previous filter type.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * // Filter to exclude internal messages while maintaining read-write
     * MemoryProvider.limitedWindow()
     *     .filtered(MemoryFilter.excludeFromAgentRole("internal"));
     *
     * // Multiple filters - same types are merged
     * // Both includeFromAgentId calls are merged, then excludeFromAgentRole applied
     * MemoryProvider.limitedWindow()
     *     .filtered(MemoryFilter.includeFromAgentId("agent-1")
     *                           .includeFromAgentId("agent-2")
     *                           .excludeFromAgentRole("debug"));
     * }</pre>
     *
     * @param filtersSupplier a supplier that provides the list of filters to apply
     * @return A new memory provider with the specified filters
     */
    public MemoryProvider filtered(MemoryFilter.MemoryFilterSupplier filtersSupplier) {
      return new LimitedWindowMemoryProvider(Optional.empty(), read, write, filtersSupplier.get());
    }
  }

  /**
   * Creates a custom memory provider using the specified SessionMemory implementation.
   *
   * <p>This allows for complete customization of memory management behavior.
   *
   * @param sessionMemory The custom SessionMemory implementation
   * @return A new custom memory provider
   */
  static CustomMemoryProvider custom(SessionMemory sessionMemory) {
    return new CustomMemoryProvider(sessionMemory);
  }

  /**
   * Memory provider that uses a custom SessionMemory implementation.
   *
   * <p>This provider allows for complete customization of memory management behavior by delegating
   * to the provided SessionMemory implementation.
   */
  record CustomMemoryProvider(SessionMemory sessionMemory) implements MemoryProvider {

    /**
     * Returns the underlying SessionMemory implementation.
     *
     * @return The SessionMemory implementation used by this provider
     */
    public SessionMemory sessionMemory() {
      return sessionMemory;
    }
  }
}
