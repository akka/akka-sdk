package com.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.domain.AllocationRule;
import com.example.domain.UserActivity;
import com.example.domain.UserActivityEvent;
import java.time.Instant;
import java.util.List;
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

  // Commands

  public record AllocateCommand(
      String reservationId,
      String userId,
      String poolId,
      int shardId,
      List<AllocationRule> allocationRules) {}

  public Effect<UserActivity.ValidationResult> allocate(AllocateCommand command) {
    logger.debug(
        "Validating allocation for reservation [{}] in pool [{}]",
        command.reservationId(),
        command.poolId());

    // Verify the poolId matches this entity's pool
    if (!currentState().poolId().equals(command.poolId())) {
      logger.warn(
          "Pool ID mismatch: entity {} is for pool {}, but request is for pool {}",
          entityId,
          currentState().poolId(),
          command.poolId());
      return effects().error("Invalid pool ID");
    }

    // Validate against allocation rules
    UserActivity.ValidationResult validationResult =
        currentState().validateAllocation(command.allocationRules());

    return switch (validationResult) {
      case UserActivity.ValidationResult.Approved approved -> {
        UserActivityEvent.AllocationApproved event =
            new UserActivityEvent.AllocationApproved(
                command.reservationId(),
                command.userId(),
                command.poolId(),
                command.shardId(),
                Instant.now());

        yield effects().persist(event).thenReply(newState -> validationResult);
      }
      case UserActivity.ValidationResult.Rejected rejected -> {
        UserActivityEvent.AllocationRejected event =
            new UserActivityEvent.AllocationRejected(
                command.reservationId(),
                command.userId(),
                command.poolId(),
                command.shardId(),
                rejected.reason(),
                Instant.now());

        yield effects().persist(event).thenReply(newState -> validationResult);
      }
    };
  }

  public ReadOnlyEffect<UserActivity> getAllocationHistory() {
    return effects().reply(currentState());
  }

  // Event handling

  @Override
  public UserActivity applyEvent(UserActivityEvent event) {
    return switch (event) {
      case UserActivityEvent.AllocationApproved evt ->
          currentState().withApprovedAllocation(evt.reservationId());
      case UserActivityEvent.AllocationRejected evt ->
          currentState().withRejectedAllocation(evt.reservationId(), evt.reason());
    };
  }
}
