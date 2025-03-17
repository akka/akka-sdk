package com.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.example.domain.UserActivityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("allocation-coordinator")
@Consume.FromEventSourcedEntity(UserActivityEntity.class)
public class AllocationCoordinator extends Consumer {

  private static final Logger logger = LoggerFactory.getLogger(AllocationCoordinator.class);
  private final ComponentClient componentClient;

  public AllocationCoordinator(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(UserActivityEvent event) {
    String shardEntityId = CapacityShardEntity.formatEntityId(event.poolId(), event.shardId());

    var capacityShardUpdated =
        switch (event) {
          case UserActivityEvent.AllocationApproved approved -> {
            logger.debug(
                "Confirming allocation for user [{}]"
                    + " (pool [{}], shard [{}], reservation [{}])",
                approved.userId(),
                approved.poolId(),
                approved.shardId(),
                approved.reservationId());

            var command =
                new CapacityShardEntity.ConfirmAllocationCommand(approved.reservationId());

            yield componentClient
                .forEventSourcedEntity(shardEntityId)
                .method(CapacityShardEntity::confirmAllocation)
                .invokeAsync(command);
          }

          case UserActivityEvent.AllocationRejected rejected -> {
            logger.debug(
                "Releasing reservation for user [{}]"
                    + " (pool [{}], shard [{}], reservation [{}], reason [{}])",
                rejected.userId(),
                rejected.poolId(),
                rejected.shardId(),
                rejected.reservationId(),
                rejected.reason());

            var command =
                new CapacityShardEntity.ReleaseReservationCommand(
                    rejected.reservationId(), "Validation failed: " + rejected.reason());

            yield componentClient
                .forEventSourcedEntity(shardEntityId)
                .method(CapacityShardEntity::releaseReservation)
                .invokeAsync(command);
          }
        };

    return effects().asyncDone(capacityShardUpdated);
  }
}
