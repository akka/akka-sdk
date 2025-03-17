package com.example.domain;

import akka.javasdk.annotations.TypeName;
import java.time.Instant;

public sealed interface CapacityShardEvent {

  @TypeName("shard-initialized")
  record ShardInitialized(String poolId, int shardId, int totalCapacity, Instant timestamp)
      implements CapacityShardEvent {}

  @TypeName("capacity-reserved")
  record CapacityReserved(String poolId, int shardId, PendingReservation reservation)
      implements CapacityShardEvent {}

  @TypeName("allocation-confirmed")
  record AllocationConfirmed(String poolId, int shardId, String reservationId, Instant timestamp)
      implements CapacityShardEvent {}

  @TypeName("reservation-released")
  record ReservationReleased(
      String poolId, int shardId, String reservationId, Instant timestamp, String reason)
      implements CapacityShardEvent {}
}
