package com.example.domain;

import java.time.Instant;
import java.util.List;

public record CapacityPool(
    String poolId,
    String name,
    String description,
    int totalCapacity,
    int numShards,
    List<AllocationRule> allocationRules,
    Instant createdAt) {
  public CapacityPool() {
    this("", "", "", 0, 0, List.of(), Instant.EPOCH);
  }
}
