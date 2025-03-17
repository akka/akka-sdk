package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.domain.CapacityShard;
import com.example.domain.CapacityShardEvent;
import com.example.domain.PendingReservation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("capacity-shard")
public class CapacityShardEntity extends EventSourcedEntity<CapacityShard, CapacityShardEvent> {

  private static final Logger logger = LoggerFactory.getLogger(CapacityShardEntity.class);
  private final String entityId;

  public CapacityShardEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public CapacityShard emptyState() {
    return new CapacityShard("", 0, 0);
  }

  public static String formatEntityId(String poolId, int shardId) {
    return String.format("%s-shard-%d", poolId, shardId);
  }

  // Commands

  public record InitializeShardCommand(String poolId, int shardId, int totalCapacity) {}

  public record ReserveCapacityCommand(String reservationId, String userId) {}

  public record ConfirmAllocationCommand(String reservationId) {}

  public record ReleaseReservationCommand(String reservationId, String reason) {}

  public record CheckUnprocessedReservationsCommand(Duration ageThreshold) {}

  // Responses

  public record ReservationResponse(
      PendingReservation reservation, CapacityShard.CapacityStatus capacityStatus) {}

  // Command handling

  public Effect<Done> initializeShard(InitializeShardCommand command) {
    if (!currentState().poolId().isEmpty()) {
      logger.debug("Shard with id [{}] already exists", entityId);
      return effects().error("Shard already exists");
    }

    if (command.totalCapacity() <= 0) {
      return effects().error("Total capacity must be greater than zero");
    }

    CapacityShardEvent.ShardInitialized event =
        new CapacityShardEvent.ShardInitialized(
            command.poolId(), command.shardId(), command.totalCapacity(), Instant.now());

    return effects().persist(event).thenReply(newState -> Done.getInstance());
  }

  public Effect<ReservationResponse> reserveCapacity(ReserveCapacityCommand command) {
    if (currentState().poolId().isEmpty()) {
      logger.debug("Shard with id [{}] does not exist", entityId);
      return effects().error("Shard not initialized");
    }

    if (currentState().availableCapacity() <= 0) {
      logger.debug("No available capacity in shard with id [{}]", entityId);
      return effects().error("No available capacity in this shard");
    }

    PendingReservation reservation =
        new PendingReservation(command.reservationId(), command.userId(), Instant.now());

    CapacityShardEvent.CapacityReserved event =
        new CapacityShardEvent.CapacityReserved(
            currentState().poolId(), currentState().shardId(), reservation);

    return effects()
        .persist(event)
        .thenReply(
            newState -> {
              // Create a capacity status snapshot for the response
              CapacityShard.CapacityStatus status = newState.getCapacityStatus();
              return new ReservationResponse(reservation, status);
            });
  }

  public Effect<Done> confirmAllocation(ConfirmAllocationCommand command) {
    if (currentState().poolId().isEmpty()) {
      logger.debug("Shard with id [{}] does not exist", entityId);
      return effects().error("Shard not initialized");
    }

    if (!currentState().pendingReservations().containsKey(command.reservationId())) {
      logger.debug(
          "Reservation with id [{}] not found in shard [{}]", command.reservationId(), entityId);
      return effects().error("Reservation not found");
    }

    CapacityShardEvent.AllocationConfirmed event =
        new CapacityShardEvent.AllocationConfirmed(
            currentState().poolId(),
            currentState().shardId(),
            command.reservationId(),
            Instant.now());

    return effects().persist(event).thenReply(newState -> Done.getInstance());
  }

  public Effect<Done> releaseReservation(ReleaseReservationCommand command) {
    if (currentState().poolId().isEmpty()) {
      logger.debug("Shard with id [{}] does not exist", entityId);
      return effects().error("Shard not initialized");
    }

    if (!currentState().pendingReservations().containsKey(command.reservationId())) {
      // may already be confirmed or released
      return effects().reply(Done.getInstance());
    }

    CapacityShardEvent.ReservationReleased event =
        new CapacityShardEvent.ReservationReleased(
            currentState().poolId(),
            currentState().shardId(),
            command.reservationId(),
            Instant.now(),
            command.reason());

    return effects().persist(event).thenReply(newState -> Done.getInstance());
  }

  public Effect<List<PendingReservation>> checkUnprocessedReservations(
      CheckUnprocessedReservationsCommand command) {
    if (currentState().poolId().isEmpty()) {
      logger.debug("Shard with id [{}] does not exist", entityId);
      return effects().error("Shard not initialized");
    }

    Instant threshold = Instant.now().minus(command.ageThreshold());

    // Find reservation IDs that are older than the threshold
    return effects().reply(currentState().getReservationsBeforeTime(threshold));
  }

  public ReadOnlyEffect<CapacityShard.CapacityStatus> getCapacityStatus() {
    if (currentState().poolId().isEmpty()) {
      logger.debug("Shard with id [{}] does not exist", entityId);
      return effects().error("Shard not initialized");
    }

    return effects().reply(currentState().getCapacityStatus());
  }

  // Event handling

  @Override
  public CapacityShard applyEvent(CapacityShardEvent event) {
    return switch (event) {
      case CapacityShardEvent.ShardInitialized evt -> handleShardInitialized(evt);
      case CapacityShardEvent.CapacityReserved evt -> handleCapacityReserved(evt);
      case CapacityShardEvent.AllocationConfirmed evt -> handleAllocationConfirmed(evt);
      case CapacityShardEvent.ReservationReleased evt -> handleReservationReleased(evt);
    };
  }

  private CapacityShard handleShardInitialized(CapacityShardEvent.ShardInitialized event) {
    return new CapacityShard(event.poolId(), event.shardId(), event.totalCapacity());
  }

  private CapacityShard handleCapacityReserved(CapacityShardEvent.CapacityReserved event) {
    return currentState().withPendingReservation(event.reservation());
  }

  private CapacityShard handleAllocationConfirmed(CapacityShardEvent.AllocationConfirmed event) {
    return currentState().withConfirmedAllocation(event.reservationId());
  }

  private CapacityShard handleReservationReleased(CapacityShardEvent.ReservationReleased event) {
    return currentState().withReleasedReservation(event.reservationId());
  }
}
