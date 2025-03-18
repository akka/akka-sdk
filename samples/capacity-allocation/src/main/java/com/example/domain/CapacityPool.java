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

  public record CreatePool(
      String poolId,
      String name,
      String description,
      int totalCapacity,
      int numShards,
      List<AllocationRule> allocationRules) {}
}
