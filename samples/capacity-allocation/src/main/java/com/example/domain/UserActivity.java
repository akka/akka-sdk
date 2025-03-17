package com.example.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record UserActivity(
    String poolId,
    String userId,
    int currentAllocations,
    List<AllocationRecord> allocationHistory,
    Instant lastActivityTime) {

  public UserActivity() {
    this("", "", 0, Collections.emptyList(), Instant.EPOCH);
  }

  public UserActivity(String poolId, String userId) {
    this(poolId, userId, 0, new ArrayList<>(), Instant.now());
  }

  public record AllocationRecord(
      String reservationId, Instant timestamp, boolean approved, Optional<String> rejectionReason) {

    public static AllocationRecord approved(String reservationId, Instant timestamp) {
      return new AllocationRecord(reservationId, timestamp, true, Optional.empty());
    }

    public static AllocationRecord rejected(
        String reservationId, Instant timestamp, String reason) {
      return new AllocationRecord(reservationId, timestamp, false, Optional.ofNullable(reason));
    }
  }

  /** Check if an allocation request is valid according to the provided rules */
  public ValidationResult validateAllocation(List<AllocationRule> rules) {
    for (AllocationRule rule : rules) {
      if (rule instanceof AllocationRule.MaxPerUserRule maxRule) {
        if (currentAllocations >= maxRule.maxAllocation()) {
          return ValidationResult.rejected(
              "User has reached maximum allocation limit of " + maxRule.maxAllocation());
        }
      }
      // Add validation for other rule types as they are implemented
    }
    return ValidationResult.approved();
  }

  /** Create a new UserActivity with an added approved allocation record */
  public UserActivity withApprovedAllocation(String reservationId) {
    int newAllocations = currentAllocations + 1;

    List<AllocationRecord> updatedHistory = new ArrayList<>(allocationHistory);
    updatedHistory.add(AllocationRecord.approved(reservationId, Instant.now()));

    return new UserActivity(poolId, userId, newAllocations, updatedHistory, Instant.now());
  }

  /** Create a new UserActivity with an added rejected allocation record */
  public UserActivity withRejectedAllocation(String reservationId, String reason) {
    // Note: currentAllocations is not incremented for rejected allocations

    List<AllocationRecord> updatedHistory = new ArrayList<>(allocationHistory);
    updatedHistory.add(AllocationRecord.rejected(reservationId, Instant.now(), reason));

    return new UserActivity(poolId, userId, currentAllocations, updatedHistory, Instant.now());
  }

  /** Result of allocation validation */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "resultType")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = ValidationResult.Approved.class, name = "approved"),
    @JsonSubTypes.Type(value = ValidationResult.Rejected.class, name = "rejected")
  })
  public sealed interface ValidationResult {
    @JsonTypeName("approved")
    record Approved() implements ValidationResult {}

    @JsonTypeName("rejected")
    record Rejected(String reason) implements ValidationResult {}

    static ValidationResult approved() {
      return new Approved();
    }

    static ValidationResult rejected(String reason) {
      return new Rejected(reason);
    }
  }
}
