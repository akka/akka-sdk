package com.example.domain;

public record CapacityShard(
    String poolId,
    int shardId,
    int totalCapacity,
    int allocatedCapacity,
    int numShards,
    String shardName) {

  // Commands

  public record InitializeShard(String poolId, int shardId, int totalCapacity, int numShards) {}

  public record ReserveCapacity(String userId, String requestId) {}

  /** Capacity status snapshot for client information */
  public record CapacityStatus(int totalCapacity, int allocatedCapacity) {}

  public static CapacityShard initial(
      String poolId, int shardId, int totalCapacity, int numShards) {
    return new CapacityShard(
        poolId, shardId, totalCapacity, 0, numShards, generateShardName(shardId, numShards));
  }

  private static String generateShardName(int shardId, int numShards) {
    int digits = String.valueOf(numShards - 1).length();
    return String.format("%0" + digits + "d", shardId);
  }

  public int availableCapacity() {
    return totalCapacity - allocatedCapacity;
  }

  public CapacityStatus status() {
    return new CapacityStatus(totalCapacity, allocatedCapacity);
  }

  public CapacityShard incrementAllocations() {
    return new CapacityShard(
        poolId, shardId, totalCapacity, allocatedCapacity + 1, numShards, shardName);
  }
}
