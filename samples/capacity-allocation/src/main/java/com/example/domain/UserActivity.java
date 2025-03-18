package com.example.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record UserActivity(
    String poolId,
    String userId,
    int currentAllocations,
    Map<String, Allocation> allocationsByRequestId,
    Instant lastActivityTime) {

  public UserActivity(String poolId, String userId) {
    this(poolId, userId, 0, new HashMap<>(), Instant.now());
  }

  // Commands

  public record RequestAllocation(String requestId, List<AllocationRule> rules) {}

  public record ConfirmAllocation(String requestId) {}

  public record RejectAllocation(String requestId, String reason) {}

  public record CancelAllocation(String requestId, String reason) {}

  // Allocations

  public enum AllocationStatus {
    ACCEPTED, // Request validated, waiting for capacity reservation
    CONFIRMED, // Capacity reserved and confirmed
    REJECTED, // Request rejected (either validation or capacity unavailable)
    CANCELLED // Request was accepted but later cancelled (by timeout)
  }

  public record Allocation(
      String requestId, Instant timestamp, AllocationStatus status, Optional<String> statusReason) {

    public static Allocation accepted(String requestId, Instant timestamp) {
      return new Allocation(requestId, timestamp, AllocationStatus.ACCEPTED, Optional.empty());
    }

    public static Allocation confirmed(String requestId, Instant timestamp) {
      return new Allocation(requestId, timestamp, AllocationStatus.CONFIRMED, Optional.empty());
    }

    public static Allocation rejected(String requestId, Instant timestamp, String reason) {
      return new Allocation(requestId, timestamp, AllocationStatus.REJECTED, Optional.of(reason));
    }

    public static Allocation cancelled(String requestId, Instant timestamp, String reason) {
      return new Allocation(requestId, timestamp, AllocationStatus.CANCELLED, Optional.of(reason));
    }
  }

  // Check if a request with this ID exists
  public boolean containsRequest(String requestId) {
    return allocationsByRequestId.containsKey(requestId);
  }

  // Get the status of a request
  public Optional<Allocation> getRequestStatus(String requestId) {
    return Optional.ofNullable(allocationsByRequestId.get(requestId));
  }

  /** Check if an allocation request is valid according to the provided rules */
  public ValidationResult validateAllocationRequest(String requestId, List<AllocationRule> rules) {
    // return current status if already validated previously
    if (allocationsByRequestId.containsKey(requestId)) {
      Allocation record = allocationsByRequestId.get(requestId);
      return switch (record.status()) {
        case ACCEPTED -> ValidationResult.accepted(requestId, "Request already accepted");
        case CONFIRMED -> ValidationResult.confirmed(requestId);
        case REJECTED ->
            ValidationResult.rejected(
                requestId, record.statusReason().orElse("Previously rejected"));
        case CANCELLED ->
            ValidationResult.rejected(
                requestId, record.statusReason().orElse("Previously cancelled"));
      };
    }

    for (AllocationRule rule : rules) {
      if (rule instanceof AllocationRule.MaxPerUserRule maxRule) {
        if (currentAllocations >= maxRule.maxAllocation()) {
          return ValidationResult.rejected(
              requestId, "User has reached maximum allocation limit of " + maxRule.maxAllocation());
        }
      }
      // Add validation for other rule types as they are implemented
    }

    return ValidationResult.accepted(requestId, null);
  }

  /** Create a new UserActivity with an added accepted allocation record */
  public UserActivity withAcceptedRequest(String requestId) {
    Map<String, Allocation> updatedAllocations = new HashMap<>(allocationsByRequestId);
    updatedAllocations.put(requestId, Allocation.accepted(requestId, Instant.now()));
    return new UserActivity(poolId, userId, currentAllocations, updatedAllocations, Instant.now());
  }

  /** Create a new UserActivity with a confirmed allocation */
  public UserActivity withConfirmedAllocation(String requestId) {
    int newAllocations = currentAllocations + 1;
    Map<String, Allocation> updatedAllocations = new HashMap<>(allocationsByRequestId);
    updatedAllocations.put(requestId, Allocation.confirmed(requestId, Instant.now()));
    return new UserActivity(poolId, userId, newAllocations, updatedAllocations, Instant.now());
  }

  /** Create a new UserActivity with a rejected allocation record */
  public UserActivity withRejectedAllocation(String requestId, String reason) {
    Map<String, Allocation> updatedAllocations = new HashMap<>(allocationsByRequestId);
    updatedAllocations.put(requestId, Allocation.rejected(requestId, Instant.now(), reason));
    return new UserActivity(poolId, userId, currentAllocations, updatedAllocations, Instant.now());
  }

  /** Create a new UserActivity with a cancelled allocation record */
  public UserActivity withCancelledAllocation(String requestId, String reason) {
    Map<String, Allocation> updatedAllocations = new HashMap<>(allocationsByRequestId);
    updatedAllocations.put(requestId, Allocation.cancelled(requestId, Instant.now(), reason));
    return new UserActivity(poolId, userId, currentAllocations, updatedAllocations, Instant.now());
  }

  /** Result of allocation validation */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "resultType")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = ValidationResult.Accepted.class, name = "accepted"),
    @JsonSubTypes.Type(value = ValidationResult.Confirmed.class, name = "confirmed"),
    @JsonSubTypes.Type(value = ValidationResult.Rejected.class, name = "rejected")
  })
  public sealed interface ValidationResult {
    String requestId();

    @JsonTypeName("accepted")
    record Accepted(String requestId, String message) implements ValidationResult {}

    @JsonTypeName("confirmed")
    record Confirmed(String requestId) implements ValidationResult {}

    @JsonTypeName("rejected")
    record Rejected(String requestId, String reason) implements ValidationResult {}

    static ValidationResult accepted(String requestId, String message) {
      return new Accepted(requestId, message);
    }

    static ValidationResult confirmed(String requestId) {
      return new Confirmed(requestId);
    }

    static ValidationResult rejected(String requestId, String reason) {
      return new Rejected(requestId, reason);
    }
  }
}
