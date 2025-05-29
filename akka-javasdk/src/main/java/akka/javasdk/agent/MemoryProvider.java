/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Interface for configuring memory management in agent systems.
 * <p>
 * MemoryProvider defines how conversation history is stored and retrieved during agent interactions.
 * It offers several implementation strategies:
 * <ul>
 *   <li>Configuration-based memory management via {@link FromConfig}</li>
 *   <li>Limited window memory management via {@link LimitedWindowMemoryProvider}</li>
 *   <li>Custom memory implementation via {@link CustomMemoryProvider}</li>
 * </ul>
 */
public sealed interface MemoryProvider {

  /**
   * Creates a memory provider based on configuration settings.
   *
   * @param configPath Path to the configuration. If empty, uses the default path "akka.javasdk.agent.memory"
   * @return A configuration-based model provider
   */
  static ModelProvider fromConfig(String configPath) {
    return new ModelProvider.FromConfig(configPath);
  }

  /**
   * Configuration-based memory provider that reads settings from the specified path.
   */
  record FromConfig(String configPath) implements MemoryProvider {}

  /**
   * Creates a limited window memory provider with default settings.
   * <p>
   * The default settings include:
   * <ul>
   *   <li>No limit on total length in bytes</li>
   *   <li>No limit on history messages</li>
   *   <li>Both read and write operations enabled</li>
   * </ul>
   *
   * @return A new limited window memory provider with default settings
   */
  static LimitedWindowMemoryProvider limitedWindow() {
    return new LimitedWindowMemoryProvider(0, Integer.MAX_VALUE, true, true);
  }

  /**
   * Memory provider that limits conversation history based on size or message count.
   * <p>
   * This provider allows fine-grained control over memory usage by limiting:
   * <ul>
   *   <li>Total size of stored conversation history in bytes</li>
   *   <li>Maximum number of messages to retain</li>
   *   <li>Whether reading from memory is enabled</li>
   *   <li>Whether writing to memory is enabled</li>
   * </ul>
   */
  record LimitedWindowMemoryProvider(
      long totalLengthInBytes,
      int historyLimit,
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
      return new LimitedWindowMemoryProvider(totalLengthInBytes, historyLimit, true, false);
    }

    /**
     * Creates a write-only version of this memory provider.
     * <p>
     * The returned provider will allow writing to memory but disable reading.
     *
     * @return A new memory provider with reading disabled
     */
    public MemoryProvider writeOnly() {
      return new LimitedWindowMemoryProvider(totalLengthInBytes, historyLimit, false, true);
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
      return new LimitedWindowMemoryProvider(totalLengthInBytes, historyLimit, read, write);
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