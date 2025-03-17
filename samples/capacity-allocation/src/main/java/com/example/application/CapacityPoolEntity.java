package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.domain.AllocationRule;
import com.example.domain.CapacityPool;
import com.example.domain.CapacityPoolEvent;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("capacity-pool")
public class CapacityPoolEntity extends EventSourcedEntity<CapacityPool, CapacityPoolEvent> {

  private static final Logger logger = LoggerFactory.getLogger(CapacityPoolEntity.class);
  private final String entityId;

  public CapacityPoolEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public CapacityPool emptyState() {
    return new CapacityPool();
  }

  // Commands

  public record CreatePoolCommand(
      String poolId,
      String name,
      String description,
      int totalCapacity,
      int numShards,
      List<AllocationRule> allocationRules) {}

  public Effect<Done> createPool(CreatePoolCommand command) {
    if (!currentState().poolId().isEmpty()) {
      logger.debug("Pool with id [{}] already exists", entityId);
      return effects().error("Pool already exists");
    }

    if (command.totalCapacity() <= 0) {
      return effects().error("Total capacity must be greater than zero");
    }

    if (command.numShards() <= 0) {
      return effects().error("Number of shards must be greater than zero");
    }

    CapacityPoolEvent.PoolCreated event =
        new CapacityPoolEvent.PoolCreated(
            command.poolId(),
            command.name(),
            command.description(),
            command.totalCapacity(),
            command.numShards(),
            command.allocationRules(),
            Instant.now());

    return effects().persist(event).thenReply(newState -> Done.getInstance());
  }

  public ReadOnlyEffect<CapacityPool> getPoolStatus() {
    if (currentState().poolId().isEmpty()) {
      logger.debug("Pool with id [{}] does not exist", entityId);
      return effects().error("Pool not found");
    }

    return effects().reply(currentState());
  }

  // Event handling

  @Override
  public CapacityPool applyEvent(CapacityPoolEvent event) {
    return switch (event) {
      case CapacityPoolEvent.PoolCreated evt -> handlePoolCreated(evt);
      default -> currentState();
    };
  }

  private CapacityPool handlePoolCreated(CapacityPoolEvent.PoolCreated event) {
    return new CapacityPool(
        event.poolId(),
        event.name(),
        event.description(),
        event.totalCapacity(),
        event.numShards(),
        event.allocationRules(),
        event.timestamp());
  }
}
