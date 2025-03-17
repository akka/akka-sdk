package com.example.domain;

import akka.javasdk.annotations.TypeName;
import java.time.Instant;
import java.util.List;

public sealed interface CapacityPoolEvent {

  @TypeName("pool-created")
  record PoolCreated(
      String poolId,
      String name,
      String description,
      int totalCapacity,
      int numShards,
      List<AllocationRule> allocationRules,
      Instant timestamp)
      implements CapacityPoolEvent {}
}
