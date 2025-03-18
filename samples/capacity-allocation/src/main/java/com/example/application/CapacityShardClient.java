package com.example.application;

import akka.javasdk.client.ComponentClient;
import com.example.domain.AllocationRule;
import com.example.domain.CapacityPool;
import com.example.domain.CapacityShard;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CapacityShardClient {
  private static final Logger logger = LoggerFactory.getLogger(CapacityShardClient.class);

  private final ComponentClient componentClient;
  private final String poolId;
  private final int numShards;
  private final List<AllocationRule> allocationRules;

  private final AtomicReferenceArray<ShardStatus> shardStatusTracking;

  public CapacityShardClient(ComponentClient componentClient, CapacityPool pool) {
    this.componentClient = componentClient;
    this.poolId = pool.poolId();
    this.numShards = pool.numShards();
    this.allocationRules = List.copyOf(pool.allocationRules());

    this.shardStatusTracking = new AtomicReferenceArray<>(numShards);
    for (int i = 0; i < numShards; i++) {
      shardStatusTracking.set(i, ShardStatus.AVAILABLE);
    }
  }

  public List<AllocationRule> getAllocationRules() {
    return Collections.unmodifiableList(allocationRules);
  }

  public sealed interface ReservationResult {
    record Success(List<AllocationRule> allocationRules) implements ReservationResult {}

    record Failure(String errorMessage) implements ReservationResult {}

    static ReservationResult success(List<AllocationRule> rules) {
      return new Success(rules);
    }

    static ReservationResult failure(String errorMessage) {
      return new Failure(errorMessage);
    }

    default boolean isSuccess() {
      return this instanceof Success;
    }
  }

  /** Status of a capacity shard */
  public enum ShardStatus {
    AVAILABLE(2), // Plenty of available capacity
    LOW_CAPACITY(1), // Less than 20% available capacity
    FULLY_ALLOCATED(0); // No available capacity

    private final int preferenceValue;

    ShardStatus(int preferenceValue) {
      this.preferenceValue = preferenceValue;
    }

    public int getPreferenceValue() {
      return preferenceValue;
    }
  }

  /**
   * Attempts to reserve capacity for a user. Uses the primary shard selection based on userId hash,
   * with fallback to "power of two choices" if the primary shard is exhausted.
   */
  public CompletionStage<ReservationResult> reserveCapacity(String userId, String requestId) {
    // Determine primary shard based on userId
    int primaryShard = selectShardForUser(userId);
    logger.debug("Primary shard for user [{}] is [{}]", userId, primaryShard);

    // If primary shard is not fully allocated, try it first
    if (shardStatusTracking.get(primaryShard) != ShardStatus.FULLY_ALLOCATED) {
      return tryReservationOnShard(primaryShard, userId, requestId)
          .thenCompose(
              result -> {
                if (result.isSuccess()) {
                  // Asynchronously fetch status of some other random shard to keep statuses updated
                  refreshRandomShardStatusAsync();
                  return CompletableFuture.completedStage(result);
                }
                // Primary shard failed, try fallback
                return tryFallbackShards(userId, requestId, new BitSet(numShards));
              });
    } else {
      // Primary shard is fully allocated, go directly to fallback
      BitSet triedShards = new BitSet(numShards);
      triedShards.set(primaryShard); // Mark primary shard as already tried
      return tryFallbackShards(userId, requestId, triedShards);
    }
  }

  /**
   * Attempt to reserve capacity using fallback shards, exhausting all available options. Will try
   * until all shards have been attempted or capacity is found.
   */
  private CompletionStage<ReservationResult> tryFallbackShards(
      String userId, String requestId, BitSet triedShards) {

    // If we've tried all shards, give up
    if (triedShards.cardinality() >= numShards) {
      return CompletableFuture.completedStage(
          ReservationResult.failure("No capacity available in any shard"));
    }

    // Use power of two choices for next attempt
    return tryPowerOfTwoChoices(userId, requestId, triedShards)
        .thenCompose(
            result -> {
              if (result.isSuccess()) {
                return CompletableFuture.completedStage(result);
              }

              // Updated tried shards from latest attempt - recursive call will try remaining shards
              return tryFallbackShards(userId, requestId, triedShards);
            });
  }

  /**
   * Power of Two Choices algorithm for load balancing: 1. Choose two random shards (excluding
   * already tried ones and fully allocated ones); 2. Check capacity status of both; 3. Use the one
   * with more available capacity.
   */
  private CompletionStage<ReservationResult> tryPowerOfTwoChoices(
      String userId, String requestId, BitSet triedShards) {

    int[] candidates = selectTwoRandomShards(triedShards);
    if (candidates == null) {
      // No viable candidates left
      return CompletableFuture.completedStage(
          ReservationResult.failure("No capacity available in any remaining shards"));
    }

    int firstChoice = candidates[0];
    int secondChoice = candidates[1];

    // Mark both candidates as tried
    triedShards.set(firstChoice);
    triedShards.set(secondChoice);

    logger.debug(
        "Power of two choices for user [{}]: shards [{}] and [{}]",
        userId,
        firstChoice,
        secondChoice);

    // Check status of both shards
    ShardStatus firstStatus = shardStatusTracking.get(firstChoice);
    ShardStatus secondStatus = shardStatusTracking.get(secondChoice);

    // Choose the better shard based on status
    int selectedShard;
    if (compareShardStatus(firstStatus, secondStatus) <= 0) {
      selectedShard = firstChoice;
    } else {
      selectedShard = secondChoice;
    }

    logger.debug(
        "Selected shard [{}] for user [{}] based on status comparison", selectedShard, userId);

    // Try the chosen shard
    return tryReservationOnShard(selectedShard, userId, requestId)
        .thenCompose(
            result -> {
              if (result.isSuccess()) {
                // Also fetch the status of the alternate shard to keep our state updated
                int alternateShard = (selectedShard == firstChoice) ? secondChoice : firstChoice;
                fetchShardStatusAsync(alternateShard);

                return CompletableFuture.completedStage(result);
              }

              // If this shard failed, try the other one
              int alternateShard = (selectedShard == firstChoice) ? secondChoice : firstChoice;

              // If the alternate shard is fully allocated, don't bother trying
              if (shardStatusTracking.get(alternateShard) == ShardStatus.FULLY_ALLOCATED) {
                // This will continue with tryFallbackShards since both were marked as tried
                return CompletableFuture.completedStage(
                    ReservationResult.failure("Both candidate shards unavailable"));
              }

              return tryReservationOnShard(alternateShard, userId, requestId);
            });
  }

  /** Compare two shard statuses for preference - lower values are preferred */
  private int compareShardStatus(ShardStatus first, ShardStatus second) {
    return second.getPreferenceValue() - first.getPreferenceValue();
  }

  /**
   * Select two random shards, excluding ones that are fully allocated or already tried. Returns
   * null if fewer than 2 viable candidates remain.
   */
  private int[] selectTwoRandomShards(BitSet triedShards) {
    // Create a list of viable shard indices
    List<Integer> viableCandidates = new ArrayList<>();
    for (int i = 0; i < numShards; i++) {
      if (!triedShards.get(i) && shardStatusTracking.get(i) != ShardStatus.FULLY_ALLOCATED) {
        viableCandidates.add(i);
      }
    }

    // Check if we have enough candidates
    final int candidatesSize = viableCandidates.size();
    if (candidatesSize == 0) {
      return null; // No viable candidates
    }
    if (candidatesSize == 1) {
      // Just try the one remaining shard
      int shard = viableCandidates.get(0);
      return new int[] {shard, shard}; // Return same shard twice
    }

    // Select 2 random distinct candidates
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int idx1 = random.nextInt(candidatesSize);
    int shard1 = viableCandidates.get(idx1);

    // Select second shard (ensuring it's different from the first)
    int idx2;
    do {
      idx2 = random.nextInt(candidatesSize);
    } while (idx2 == idx1 && candidatesSize > 1);

    int shard2 = viableCandidates.get(idx2);
    return new int[] {shard1, shard2};
  }

  /** Try to reserve capacity on a specific shard */
  private CompletionStage<ReservationResult> tryReservationOnShard(
      int shardId, String userId, String requestId) {

    String shardEntityId = CapacityShardEntity.formatEntityId(poolId, shardId);
    var command = new CapacityShard.ReserveCapacity(userId, requestId);

    return componentClient
        .forEventSourcedEntity(shardEntityId)
        .method(CapacityShardEntity::reserveCapacity)
        .invokeAsync(command)
        .thenApply(
            capacityStatus -> {
              updateShardStatus(shardId, capacityStatus);
              return ReservationResult.success(allocationRules);
            })
        .exceptionally(
            ex -> {
              logger.warn("Failed to reserve capacity on shard {}: {}", shardId, ex.getMessage());
              return ReservationResult.failure(
                  "Capacity unavailable on selected shard: " + ex.getMessage());
            });
  }

  /** Update tracked status for a shard based on its capacity stats */
  private void updateShardStatus(int shardId, CapacityShard.CapacityStatus status) {
    double usageRatio = (double) status.allocatedCapacity() / status.totalCapacity();

    ShardStatus newStatus;
    if (status.allocatedCapacity() == status.totalCapacity()) {
      newStatus = ShardStatus.FULLY_ALLOCATED;
    } else if (usageRatio > 0.8) { // More than 80% used
      newStatus = ShardStatus.LOW_CAPACITY;
    } else {
      newStatus = ShardStatus.AVAILABLE;
    }

    ShardStatus oldStatus = shardStatusTracking.getAndSet(shardId, newStatus);
    if (oldStatus != newStatus) {
      logger.debug("Shard [{}] status changed from [{}] to [{}]", shardId, oldStatus, newStatus);
    }
  }

  /** Asynchronously refresh the status of a randomly chosen shard */
  private void refreshRandomShardStatusAsync() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int randomShard = random.nextInt(numShards);
    fetchShardStatusAsync(randomShard);
  }

  /** Fetch shard status asynchronously without waiting for result */
  private void fetchShardStatusAsync(int shardId) {
    String shardEntityId = CapacityShardEntity.formatEntityId(poolId, shardId);
    componentClient
        .forEventSourcedEntity(shardEntityId)
        .method(CapacityShardEntity::getCapacityStatus)
        .invokeAsync()
        .thenAccept(status -> updateShardStatus(shardId, status))
        .exceptionally(
            ex -> {
              logger.warn("Failed to refresh status for shard {}: {}", shardId, ex.getMessage());
              return null;
            });
  }

  /** Compute hash of userId to determine shard assignment */
  private int selectShardForUser(String userId) {
    return Math.abs(userId.hashCode()) % numShards;
  }
}
