package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.domain.UserActivity;
import com.example.domain.UserActivityEvent;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("user-activity")
public class UserActivityEntity extends EventSourcedEntity<UserActivity, UserActivityEvent> {

  private static final Logger logger = LoggerFactory.getLogger(UserActivityEntity.class);

  private final String entityId; // Format: "poolId:userId"
  private final String poolId;
  private final String userId;

  public UserActivityEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
    // parse poolId and userId from entityId
    String[] parts = entityId.split(":");
    if (parts.length == 2) {
      this.poolId = parts[0];
      this.userId = parts[1];
    } else {
      throw new RuntimeException("Invalid entity id format for UserActivityEntity: " + entityId);
    }
  }

  @Override
  public UserActivity emptyState() {
    return new UserActivity(poolId, userId);
  }

  public static String formatEntityId(String poolId, String userId) {
    return String.format("%s:%s", poolId, userId);
  }

  // Command handling

  public Effect<UserActivity.ValidationResult> requestAllocation(
      UserActivity.RequestAllocation command) {

    logger.debug(
        "Validating allocation request [{}] for user [{}] in pool [{}]",
        command.requestId(),
        userId,
        poolId);

    // Validate against allocation rules
    UserActivity.ValidationResult validationResult =
        currentState().validateAllocationRequest(command.requestId(), command.rules());

    String requestId = command.requestId();
    boolean requestExists = currentState().containsRequest(requestId);

    if (requestExists) {
      return effects().reply(validationResult);
    } else {
      Optional<UserActivityEvent> event =
          switch (validationResult) {
            case UserActivity.ValidationResult.Accepted __ ->
                Optional.of(
                    new UserActivityEvent.RequestAccepted(
                        poolId, userId, requestId, Instant.now()));
            case UserActivity.ValidationResult.Confirmed __ ->
                Optional.empty(); // already confirmed
            case UserActivity.ValidationResult.Rejected rejected ->
                Optional.of(
                    new UserActivityEvent.AllocationRejected(
                        poolId, userId, requestId, rejected.reason(), Instant.now()));
          };
      if (event.isPresent()) {
        return effects().persist(event.get()).thenReply(__ -> validationResult);
      } else {
        return effects().reply(validationResult);
      }
    }
  }

  public Effect<Done> confirmAllocation(UserActivity.ConfirmAllocation command) {
    logger.debug("Confirming allocation for request [{}]", command.requestId());

    if (!currentState().containsRequest(command.requestId())) {
      logger.warn("Request [{}] not found in user activity", command.requestId());
      return effects().error("Request not found");
    }

    UserActivity.Allocation allocation = currentState().getRequestStatus(command.requestId()).get();

    if (allocation.status() == UserActivity.AllocationStatus.CONFIRMED) {
      return effects().reply(Done.getInstance()); // already confirmed (idempotent)
    }

    if (allocation.status() != UserActivity.AllocationStatus.ACCEPTED) {
      return effects().error("Cannot confirm allocation that is not in ACCEPTED state");
    }

    UserActivityEvent.AllocationConfirmed event =
        new UserActivityEvent.AllocationConfirmed(
            poolId, userId, command.requestId(), Instant.now());

    return effects().persist(event).thenReply(newState -> Done.getInstance());
  }

  public Effect<Done> rejectAllocation(UserActivity.RejectAllocation command) {
    logger.debug(
        "Rejecting allocation for request [{}]: {}", command.requestId(), command.reason());

    if (!currentState().containsRequest(command.requestId())) {
      logger.warn("Request [{}] not found in user activity", command.requestId());
      return effects().error("Request not found");
    }

    UserActivity.Allocation allocation = currentState().getRequestStatus(command.requestId()).get();

    if (allocation.status() == UserActivity.AllocationStatus.REJECTED) {
      return effects().reply(Done.getInstance()); // already rejected (idempotent)
    }

    UserActivityEvent.AllocationRejected event =
        new UserActivityEvent.AllocationRejected(
            poolId, userId, command.requestId(), command.reason(), Instant.now());

    return effects().persist(event).thenReply(newState -> Done.getInstance());
  }

  public Effect<Done> cancelAllocation(UserActivity.CancelAllocation command) {
    logger.debug(
        "Cancelling allocation for request [{}]: {}", command.requestId(), command.reason());

    if (!currentState().containsRequest(command.requestId())) {
      logger.warn("Request [{}] not found in user activity, ignoring cancel", command.requestId());
      return effects().reply(Done.getInstance());
    }

    UserActivity.Allocation allocation = currentState().getRequestStatus(command.requestId()).get();

    if (allocation.status() != UserActivity.AllocationStatus.ACCEPTED) {
      logger.debug(
          "Cannot cancel allocation in {} state, ignoring (idempotent)", allocation.status());
      return effects().reply(Done.getInstance());
    }

    UserActivityEvent.AllocationCancelled event =
        new UserActivityEvent.AllocationCancelled(
            poolId, userId, command.requestId(), command.reason(), Instant.now());

    return effects().persist(event).thenReply(newState -> Done.getInstance());
  }

  public ReadOnlyEffect<UserActivity> getAllocationHistory() {
    return effects().reply(currentState());
  }

  // Event handling

  @Override
  public UserActivity applyEvent(UserActivityEvent event) {
    return switch (event) {
      case UserActivityEvent.RequestAccepted evt ->
          currentState().withAcceptedRequest(evt.requestId());

      case UserActivityEvent.AllocationConfirmed evt ->
          currentState().withConfirmedAllocation(evt.requestId());

      case UserActivityEvent.AllocationRejected evt ->
          currentState().withRejectedAllocation(evt.requestId(), evt.reason());

      case UserActivityEvent.AllocationCancelled evt ->
          currentState().withCancelledAllocation(evt.requestId(), evt.reason());
    };
  }
}
