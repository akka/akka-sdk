package com.example.application;

import com.example.domain.CapacityShard;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public class CapacityTelemetry {

  private final Gauge shardCapacityTotal =
      Gauge.build()
          .name("app_shard_capacity_total")
          .help("Total capacity assigned to a shard")
          .labelNames("pool_id", "shard_id")
          .register();

  private final Gauge shardCapacityAllocated =
      Gauge.build()
          .name("app_shard_capacity_allocated")
          .help("Currently allocated capacity in a shard")
          .labelNames("pool_id", "shard_id")
          .register();

  private final Gauge shardCapacityAvailable =
      Gauge.build()
          .name("app_shard_capacity_available")
          .help("Currently available capacity in a shard")
          .labelNames("pool_id", "shard_id")
          .register();

  private final Counter shardCapacityReservations =
      Counter.build()
          .name("app_shard_capacity_reservations_total")
          .help("Total number of capacity reservations made in a shard")
          .labelNames("pool_id", "shard_id")
          .register();

  public void shardInitialized(CapacityShard shard) {
    shardCapacityTotal.labels(shard.poolId(), shard.shardName()).set(shard.totalCapacity());
    shardCapacityAllocated.labels(shard.poolId(), shard.shardName()).set(shard.allocatedCapacity());
    shardCapacityAvailable.labels(shard.poolId(), shard.shardName()).set(shard.availableCapacity());
  }

  public void shardCapacityAllocated(CapacityShard shard) {
    shardCapacityAllocated.labels(shard.poolId(), shard.shardName()).set(shard.allocatedCapacity());
    shardCapacityAvailable.labels(shard.poolId(), shard.shardName()).set(shard.availableCapacity());
    shardCapacityReservations.labels(shard.poolId(), shard.shardName()).inc();
  }
}
