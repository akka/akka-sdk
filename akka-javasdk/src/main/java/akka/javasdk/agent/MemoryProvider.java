/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.Optional;

/**
 * Interface for configuring memory management in agent systems.
 * <p>
 * MemoryProvider defines how conversation history is stored and retrieved during agent interactions.
 * It offers several implementation strategies:
 * <ul>
 *   <li>Limited window memory management via {@link LimitedWindowMemoryProvider}</li>
 *   <li>Custom memory implementation via {@link CustomMemoryProvider}</li>
 * </ul>
 */
public sealed interface MemoryProvider {

  /**
   * Creates a configuration-based memory provider based on configuration defaults.
   * @return A configuration-based model provider
   */
  static MemoryProvider fromConfig() {
    return fromConfig("");
  }

  /**
   * Creates a memory provider based on configuration settings.
   *
   * @param configPath Path to the configuration. If empty, uses the default path "akka.javasdk.agent.memory"
   * @return A configuration-based model provider
   */
  static MemoryProvider fromConfig(String configPath) {
    return new MemoryProvider.FromConfig(configPath);
  }

  /**
   * Configuration-based memory provider that reads settings from the specified path.
   */
  record FromConfig(String configPath) implements MemoryProvider {}


  /**
   * Disabled memory provider, which does not store or retrieve conversation history.
   */
  record Disabled() implements MemoryProvider {}

  /**
   * Disabled memory provider, which does not store or retrieve conversation history.
   * @return A memory provider without memory.
   */
  static Disabled none() {
    return new Disabled();
  }

  /**
   * Creates a limited window memory provider with default settings.
   * <p>
   * The default settings are:
   * <ul>
   *   <li>Include all conversation history in each interaction with the model</li>
   *   <li>Record all interactions into memory</li>
   * </ul>
   *
   * @return A new limited window memory provider with default settings
   */
  static LimitedWindowMemoryProvider limitedWindow() {
    return new LimitedWindowMemoryProvider(Optional.empty(), true, true);
  }

  /**
   * Memory provider that limits conversation history based on size or message count.
   * <p>
   * This provider allows fine-grained control over memory usage by limiting:
   * <ul>
   *   <li>Use only last N messages from the history</li>
   *   <li>Whether reading from memory is enabled</li>
   *   <li>Whether writing to memory is enabled</li>
   * </ul>
   */
  record LimitedWindowMemoryProvider(
      Optional<Integer> readLastN,
      boolean read,
      boolean write) implements MemoryProvider {

    /**
     * Creates a read-only version of this memory provider.
     * <p>
     * The returned provider will allow reading from memory but disable writing.
     *
     * @return A new memory provider with writing disabled
     */
    public MemoryProvider readOnly() {
      return new LimitedWindowMemoryProvider(readLastN, true, false);
    }

    /**
     * Creates a write-only version of this memory provider.
     * <p>
     * The returned provider will allow writing to memory but disable reading.
     *
     * @return A new memory provider with reading disabled
     */
    public MemoryProvider writeOnly() {
      return new LimitedWindowMemoryProvider(readLastN, false, true);
    }

    /**
     * Creates a new memory provider with an updated history limit.
     * <p>
     * The history limit controls the maximum number of messages to retain in memory.
     *
     * @param onlyLastN parameter controls the maximum number of most recent messages to read from memory.
     * @return A new memory provider with the specified history limit
     */
    public MemoryProvider readLastN(int onlyLastN) {
      return new LimitedWindowMemoryProvider(Optional.of(onlyLastN), read, write);
    }
  }

  /**
   * Creates a custom memory provider using the specified CoreMemory implementation.
   * <p>
   * This allows for complete customization of memory management behavior.
   *
   * @param coreMemory The custom CoreMemory implementation
   * @return A new custom memory provider
   */
  static CustomMemoryProvider custom(CoreMemory coreMemory) {
    return new CustomMemoryProvider(coreMemory);
  }

  /**
   * Memory provider that uses a custom CoreMemory implementation.
   * <p>
   * This provider allows for complete customization of memory management behavior
   * by delegating to the provided CoreMemory implementation.
   */
  record CustomMemoryProvider(CoreMemory coreMemory) implements MemoryProvider {

    /**
     * Returns the underlying CoreMemory implementation.
     *
     * @return The CoreMemory implementation used by this provider
     */
    public CoreMemory coreMemory() {
      return coreMemory;
    }
  }

}
