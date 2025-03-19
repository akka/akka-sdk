package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.domain.CapacityShard;
import com.example.domain.CapacityShardEvent;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("capacity-shard")
public class CapacityShardEntity extends EventSourcedEntity<CapacityShard, CapacityShardEvent> {

  private static final Logger logger = LoggerFactory.getLogger(CapacityShardEntity.class);

  private final String entityId;

  private final CapacityTelemetry telemetry;

  public CapacityShardEntity(EventSourcedEntityContext context, CapacityTelemetry telemetry) {
    this.entityId = context.entityId();
    this.telemetry = telemetry;
  }

  public static String formatEntityId(String poolId, int shardId) {
    return String.format("%s-shard-%d", poolId, shardId);
  }

  // Command handling

  public Effect<Done> initializeShard(CapacityShard.InitializeShard command) {
    if (currentState() != null) {
      logger.debug("Shard with id [{}] already initialized", entityId);
      return effects().error("Shard already initialized");
    }

    if (command.totalCapacity() <= 0) {
      return effects().error("Total capacity must be greater than zero");
    }

    CapacityShardEvent.ShardInitialized event =
        new CapacityShardEvent.ShardInitialized(
            command.poolId(),
            command.shardId(),
            command.totalCapacity(),
            command.numShards(),
            Instant.now());

    return effects().persist(event).thenReply(newState -> Done.getInstance());
  }

  public Effect<CapacityShard.CapacityStatus> reserveCapacity(
      CapacityShard.ReserveCapacity command) {
    if (currentState() == null) {
      logger.debug("Shard with id [{}] not initialized", entityId);
      return effects().error("Shard not initialized");
    }

    if (currentState().availableCapacity() <= 0) {
      logger.debug("No available capacity in shard with id [{}]", entityId);
      return effects().error("No available capacity in this shard");
    }

    CapacityShardEvent.CapacityAllocated event =
        new CapacityShardEvent.CapacityAllocated(
            currentState().poolId(),
            currentState().shardId(),
            command.userId(),
            command.requestId(),
            Instant.now());

    return effects().persist(event).thenReply(newState -> newState.status());
  }

  public ReadOnlyEffect<CapacityShard.CapacityStatus> getCapacityStatus() {
    if (currentState().poolId().isEmpty()) {
      logger.warn("Shard with id [{}] does not exist", entityId);
      return effects().error("Shard not initialized");
    }

    return effects().reply(currentState().status());
  }

  // Event handling

  @Override
  public CapacityShard applyEvent(CapacityShardEvent event) {
    return switch (event) {
      case CapacityShardEvent.ShardInitialized shard -> {
        CapacityShard initialState =
            CapacityShard.initial(
                shard.poolId(), shard.shardId(), shard.totalCapacity(), shard.numShards());
        telemetry.shardInitialized(initialState);
        yield initialState;
      }

      case CapacityShardEvent.CapacityAllocated __ -> {
        CapacityShard updatedState = currentState().incrementAllocations();
        telemetry.shardCapacityAllocated(updatedState);
        yield updatedState;
      }
    };
  }
}
