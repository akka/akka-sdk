package com.example.domain;

import akka.javasdk.annotations.TypeName;
import java.time.Instant;

public sealed interface CapacityShardEvent {

  String poolId();

  int shardId();

  @TypeName("shard-initialized")
  record ShardInitialized(String poolId, int shardId, int totalCapacity, Instant timestamp)
      implements CapacityShardEvent {}

  @TypeName("capacity-allocated")
  record CapacityAllocated(
      String poolId, int shardId, String userId, String requestId, Instant timestamp)
      implements CapacityShardEvent {}
}
