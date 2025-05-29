/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

public sealed interface MemoryProvider {


  static ModelProvider fromConfig(String configPath) {
    return new ModelProvider.FromConfig(configPath);
  }

  record FromConfig(String configPath) implements MemoryProvider {}

  static LimitedWindowMemoryProvider limitedWindow() {
    return new LimitedWindowMemoryProvider(1000, Integer.MAX_VALUE,true, true);
  }

  record LimitedWindowMemoryProvider(
      long totalLengthInBytes,
      int historyLimit,
      boolean read,
      boolean write) implements MemoryProvider {

    public MemoryProvider readOnly() {
      return new LimitedWindowMemoryProvider(totalLengthInBytes, historyLimit, true, false);
    }

    public MemoryProvider writeOnly() {
      return new LimitedWindowMemoryProvider(totalLengthInBytes, historyLimit,false, true);
    }

    public MemoryProvider withHistoryLimit(int historyLimit) {
      return new LimitedWindowMemoryProvider(totalLengthInBytes, historyLimit, read, write);
    }
  }

  static CustomMemoryProvider custom(CoreMemory coreMemory) {
    return new CustomMemoryProvider(coreMemory);
  }

  record CustomMemoryProvider(CoreMemory coreMemory) implements MemoryProvider {

    public CoreMemory coreMemory() {
      return coreMemory;
    }
  }

}
