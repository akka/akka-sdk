package com.example.application;

import akka.javasdk.client.ComponentClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CapacityShardClientProvider {
  private static final Logger logger = LoggerFactory.getLogger(CapacityShardClientProvider.class);

  private final ComponentClient componentClient;
  private final ConcurrentHashMap<String, CompletableFuture<CapacityShardClient>> clientCache =
      new ConcurrentHashMap<>();

  public CapacityShardClientProvider(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public static class ClientInitializationException extends RuntimeException {
    public ClientInitializationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** Gets or creates a client for the specified capacity pool. */
  public CompletionStage<CapacityShardClient> getClientForPool(String poolId) {
    return clientCache.computeIfAbsent(
        poolId,
        id -> {
          logger.debug("Creating CapacityShardClient for pool [{}]", id);
          return createClientForPool(id).toCompletableFuture();
        });
  }

  /** Creates a new client for the specified pool by fetching pool configuration. */
  private CompletionStage<CapacityShardClient> createClientForPool(String poolId) {
    return componentClient
        .forEventSourcedEntity(poolId)
        .method(CapacityPoolEntity::getPoolStatus)
        .invokeAsync()
        .thenApply(
            pool -> {
              logger.debug(
                  "Retrieved configuration for pool [{}]: [{}] shards", poolId, pool.numShards());
              return new CapacityShardClient(componentClient, pool);
            })
        .exceptionally(
            ex -> {
              logger.error("Failed to create client for pool [{}]", poolId, ex);
              throw new ClientInitializationException(
                  "Failed to initialize capacity shard client for pool [" + poolId + "]", ex);
            });
  }

  /** Refreshes the client for a pool, which will cause a new fetch of pool configuration. */
  public void invalidateClient(String poolId) {
    logger.debug("Invalidating client for pool [{}]", poolId);
    clientCache.remove(poolId);
  }
}
