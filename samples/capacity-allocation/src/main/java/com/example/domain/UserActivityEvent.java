package com.example.domain;

import akka.javasdk.annotations.TypeName;
import java.time.Instant;

public sealed interface UserActivityEvent {

  String userId();

  String poolId();

  int shardId();

  @TypeName("allocation-approved")
  record AllocationApproved(
      String reservationId, String userId, String poolId, int shardId, Instant timestamp)
      implements UserActivityEvent {}

  @TypeName("allocation-rejected")
  record AllocationRejected(
      String reservationId,
      String userId,
      String poolId,
      int shardId,
      String reason,
      Instant timestamp)
      implements UserActivityEvent {}
}
