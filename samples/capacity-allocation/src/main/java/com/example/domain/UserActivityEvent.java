package com.example.domain;

import akka.javasdk.annotations.TypeName;
import java.time.Instant;

public sealed interface UserActivityEvent {

  String poolId();

  String userId();

  String requestId();

  @TypeName("request-accepted")
  record RequestAccepted(String poolId, String userId, String requestId, Instant timestamp)
      implements UserActivityEvent {}

  @TypeName("allocation-confirmed")
  record AllocationConfirmed(String poolId, String userId, String requestId, Instant timestamp)
      implements UserActivityEvent {}

  @TypeName("allocation-rejected")
  record AllocationRejected(
      String poolId, String userId, String requestId, String reason, Instant timestamp)
      implements UserActivityEvent {}

  @TypeName("allocation-cancelled")
  record AllocationCancelled(
      String poolId, String userId, String requestId, String reason, Instant timestamp)
      implements UserActivityEvent {}
}
