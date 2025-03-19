package com.example;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.client.ComponentClient;
import com.example.application.CapacityShardClientProvider;
import com.example.application.CapacityTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Setup
public class CapacityAllocationSetup implements ServiceSetup {
  private static final Logger logger = LoggerFactory.getLogger(CapacityAllocationSetup.class);

  private final CapacityShardClientProvider shardClientProvider;
  private final CapacityTelemetry capacityTelemetry;

  public CapacityAllocationSetup(ComponentClient componentClient) {
    this.capacityTelemetry = new CapacityTelemetry();
    this.shardClientProvider = new CapacityShardClientProvider(componentClient);
    logger.info("Capacity Allocation service initializing");
  }

  @Override
  public void onStartup() {
    logger.info("Capacity Allocation service starting up");
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    return new DependencyProvider() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> T getDependency(Class<T> clazz) {
        if (clazz.equals(CapacityShardClientProvider.class)) {
          return (T) shardClientProvider;
        }
        if (clazz.equals(CapacityTelemetry.class)) {
          return (T) capacityTelemetry;
        }
        throw new IllegalArgumentException("Unknown dependency: " + clazz.getName());
      }
    };
  }
}
