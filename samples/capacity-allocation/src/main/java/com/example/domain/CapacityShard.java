package com.example.domain;

public record CapacityShard(String poolId, int shardId, int totalCapacity, int allocatedCapacity) {

  // Commands

  public record InitializeShard(String poolId, int shardId, int totalCapacity) {}

  public record ReserveCapacity(String userId, String requestId) {}

  /** Capacity status snapshot for client information */
  public record CapacityStatus(int totalCapacity, int allocatedCapacity) {}

  public CapacityShard(String poolId, int shardId, int totalCapacity) {
    this(poolId, shardId, totalCapacity, 0);
  }

  public int availableCapacity() {
    return totalCapacity - allocatedCapacity;
  }

  public CapacityStatus getCapacityStatus() {
    return new CapacityStatus(totalCapacity, allocatedCapacity);
  }

  public CapacityShard incrementAllocations() {
    return new CapacityShard(poolId, shardId, totalCapacity, allocatedCapacity + 1);
  }
}
