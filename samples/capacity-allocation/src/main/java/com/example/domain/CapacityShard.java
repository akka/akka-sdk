package com.example.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

public record CapacityShard(
    String poolId,
    int shardId,
    int totalCapacity,
    int reservedCapacity,
    int allocatedCapacity,
    Map<String, PendingReservation> pendingReservations,
    NavigableSet<PendingReservation> pendingReservationsByTime) {

  /** Capacity status snapshot for client information */
  public record CapacityStatus(
      int totalCapacity, int allocatedCapacity, int reservedCapacity, int availableCapacity) {}

  public CapacityShard(String poolId, int shardId, int totalCapacity) {
    this(
        poolId,
        shardId,
        totalCapacity,
        0,
        0,
        new HashMap<>(),
        new TreeSet<>(PendingReservation.TIMESTAMP_COMPARATOR));
  }

  public int availableCapacity() {
    return totalCapacity - reservedCapacity - allocatedCapacity;
  }

  /** Get a snapshot of the current capacity status */
  public CapacityStatus getCapacityStatus() {
    return new CapacityStatus(
        totalCapacity, allocatedCapacity, reservedCapacity, availableCapacity());
  }

  public CapacityShard withPendingReservation(PendingReservation reservation) {
    Map<String, PendingReservation> newPendingReservations = new HashMap<>(pendingReservations);
    newPendingReservations.put(reservation.reservationId(), reservation);

    NavigableSet<PendingReservation> newPendingReservationsByTime =
        new TreeSet<>(PendingReservation.TIMESTAMP_COMPARATOR);
    newPendingReservationsByTime.addAll(pendingReservationsByTime);
    newPendingReservationsByTime.add(reservation);

    return new CapacityShard(
        poolId,
        shardId,
        totalCapacity,
        reservedCapacity + 1,
        allocatedCapacity,
        newPendingReservations,
        newPendingReservationsByTime);
  }

  public CapacityShard withConfirmedAllocation(String reservationId) {
    if (!pendingReservations.containsKey(reservationId)) {
      return this;
    }

    PendingReservation reservation = pendingReservations.get(reservationId);
    Map<String, PendingReservation> newPendingReservations = new HashMap<>(pendingReservations);
    newPendingReservations.remove(reservationId);

    NavigableSet<PendingReservation> newPendingReservationsByTime =
        new TreeSet<>(PendingReservation.TIMESTAMP_COMPARATOR);
    newPendingReservationsByTime.addAll(pendingReservationsByTime);
    newPendingReservationsByTime.remove(reservation);

    return new CapacityShard(
        poolId,
        shardId,
        totalCapacity,
        reservedCapacity - 1,
        allocatedCapacity + 1,
        newPendingReservations,
        newPendingReservationsByTime);
  }

  public CapacityShard withReleasedReservation(String reservationId) {
    if (!pendingReservations.containsKey(reservationId)) {
      return this;
    }

    PendingReservation reservation = pendingReservations.get(reservationId);
    Map<String, PendingReservation> newPendingReservations = new HashMap<>(pendingReservations);
    newPendingReservations.remove(reservationId);

    NavigableSet<PendingReservation> newPendingReservationsByTime =
        new TreeSet<>(PendingReservation.TIMESTAMP_COMPARATOR);
    newPendingReservationsByTime.addAll(pendingReservationsByTime);
    newPendingReservationsByTime.remove(reservation);

    return new CapacityShard(
        poolId,
        shardId,
        totalCapacity,
        reservedCapacity - 1,
        allocatedCapacity,
        newPendingReservations,
        newPendingReservationsByTime);
  }

  public List<PendingReservation> getReservationsBeforeTime(Instant threshold) {
    return pendingReservationsByTime
        .headSet(PendingReservation.dummyWithTimestamp(threshold), true)
        .stream()
        .toList();
  }
}
