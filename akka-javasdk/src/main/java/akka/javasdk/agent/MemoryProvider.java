/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
     * Creates a read-only version of this memory provider.
     *
     * <p>The returned provider will allow reading from memory but disable writing.
     *
     * @return A new memory provider with writing disabled
     */
    public MemoryProvider readOnly(MemoryFilter filter) {
      return new LimitedWindowMemoryProvider(readLastN, true, false, List.of(filter));
    }

    public MemoryProvider readOnly(MemoryFilter filter, MemoryFilter... filters) {
      var allFilters = Stream.concat(Stream.of(filter), Arrays.stream(filters)).toList();
      return new LimitedWindowMemoryProvider(readLastN, true, false, allFilters);
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

    public MemoryProvider readLast(int onlyLastN, MemoryFilter filter) {
      return new LimitedWindowMemoryProvider(Optional.of(onlyLastN), read, write, List.of(filter));
    }

    public MemoryProvider readLast(int onlyLastN, MemoryFilter filter, MemoryFilter... filters) {
      var allFilters = Stream.concat(Stream.of(filter), Arrays.stream(filters)).toList();
      return new LimitedWindowMemoryProvider(Optional.of(onlyLastN), read, write, allFilters);
    }

    public MemoryProvider filtered(MemoryFilter filter) {
      return new LimitedWindowMemoryProvider(Optional.empty(), read, write, List.of(filter));
    }

    public MemoryProvider filtered(MemoryFilter filter, MemoryFilter... filters) {
      var allFilters = Stream.concat(Stream.of(filter), Arrays.stream(filters)).toList();
      return new LimitedWindowMemoryProvider(Optional.empty(), read, write, allFilters);
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
