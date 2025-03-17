package com.example.domain;

import java.time.Instant;
import java.util.Comparator;

public record PendingReservation(String reservationId, String userId, Instant reservedAt) {
  /**
   * Comparator that orders PendingReservation objects by reservedAt timestamp. When timestamps are
   * equal, it uses reservationId as a secondary sort key to ensure stable ordering and prevent
   * duplicates in sorted collections.
   */
  public static final Comparator<PendingReservation> TIMESTAMP_COMPARATOR =
      Comparator.comparing(PendingReservation::reservedAt)
          .thenComparing(PendingReservation::reservationId);

  /**
   * Creates a dummy reservation with the given timestamp for use in range operations with sorted
   * collections.
   */
  public static PendingReservation dummyWithTimestamp(Instant timestamp) {
    return new PendingReservation("", "", timestamp);
  }
}
