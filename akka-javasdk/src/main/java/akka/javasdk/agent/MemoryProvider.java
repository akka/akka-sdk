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
 *   <li>Configuration-based memory management via {@link FromConfig} - uses application configuration</li>
 *   <li>Limited window memory management via {@link LimitedWindowMemoryProvider} - controls history size</li>
 *   <li>Custom memory implementation via {@link CustomMemoryProvider} - provides full control</li>
 * </ul>
 * <p>
 * Choose the appropriate implementation based on your requirements:
 * <ul>
 *   <li>Use {@link #fromConfig()} when you want to configure memory through application configuration</li>
 *   <li>Use {@link #limitedWindow()} when you need simple control over history size with default settings</li>
 *   <li>Use {@link #custom(CoreMemory)} when you need complete control over memory management</li>
 * </ul>
 */
public sealed interface MemoryProvider {

  /**
   * Creates a configuration-based memory provider based on configuration defaults.
   * @return A configuration-based memory provider
   */
  static MemoryProvider fromConfig() {
    return fromConfig("");
  }

  /**
   * Creates a memory provider based on configuration settings.
   *
   * @param configPath Path to the configuration. If empty, uses the default path "akka.javasdk.agent.memory"
   * @return A configuration-based memory provider
   */
  static MemoryProvider fromConfig(String configPath) {
    return new MemoryProvider.FromConfig(configPath);
  }

  /**
   * Configuration-based memory provider that reads settings from the specified path.
   */
  record FromConfig(String configPath) implements MemoryProvider {}

  /**
   * Creates a limited window memory provider with default settings.
   * <p>
   * The default settings are:
   * <ul>
   *   <li>No limit on the number of messages to retain (Optional.empty() for historyLimit)</li>
   *   <li>Reading from memory is enabled (read=true)</li>
   *   <li>Writing to memory is enabled (write=true)</li>
   * </ul>
   *
   * @return A new limited window memory provider with default settings
   */
  static LimitedWindowMemoryProvider limitedWindow() {
    return new LimitedWindowMemoryProvider(Optional.empty(), true, true);
  }

  /**
   * Memory provider that limits conversation history based on message count.
   * <p>
   * This provider allows fine-grained control over memory usage by limiting:
   * <ul>
   *   <li>Maximum number of messages to retain (via historyLimit)</li>
   *   <li>Whether reading from memory is enabled (via read flag)</li>
   *   <li>Whether writing to memory is enabled (via write flag)</li>
   * </ul>
   */
  record LimitedWindowMemoryProvider(
      Optional<Integer> historyLimit,
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
      return new LimitedWindowMemoryProvider(historyLimit, true, false);
    }

    /**
     * Creates a write-only version of this memory provider.
     * <p>
     * The returned provider will allow writing to memory but disable reading.
     *
     * @return A new memory provider with reading disabled
     */
    public MemoryProvider writeOnly() {
      return new LimitedWindowMemoryProvider(historyLimit, false, true);
    }

    /**
     * Creates a new memory provider with an updated history limit.
     * <p>
     * The history limit controls the maximum number of messages to retain in memory.
     *
     * @param historyLimit Maximum number of messages to retain
     * @return A new memory provider with the specified history limit
     */
    public MemoryProvider withHistoryLimit(int historyLimit) {
      return new LimitedWindowMemoryProvider(Optional.of(historyLimit), read, write);
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
    // No need for explicit coreMemory() accessor method as it's automatically generated by the record
  }
}