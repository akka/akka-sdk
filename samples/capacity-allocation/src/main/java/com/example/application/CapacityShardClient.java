package com.example.application;

import akka.javasdk.client.ComponentClient;
import com.example.domain.AllocationRule;
import com.example.domain.CapacityPool;
import com.example.domain.CapacityShard;
import com.example.domain.PendingReservation;
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

  // Status tracking for each shard
  private final AtomicReferenceArray<ShardStatus> shardStatuses;

  public CapacityShardClient(ComponentClient componentClient, CapacityPool pool) {
    this.componentClient = componentClient;
    this.poolId = pool.poolId();
    this.numShards = pool.numShards();
    this.allocationRules = List.copyOf(pool.allocationRules());

    this.shardStatuses = new AtomicReferenceArray<>(numShards);
    for (int i = 0; i < numShards; i++) {
      shardStatuses.set(i, ShardStatus.AVAILABLE);
    }
  }

  public sealed interface ReservationResult {
    record Success(
        PendingReservation reservation, List<AllocationRule> allocationRules, int selectedShardId)
        implements ReservationResult {}

    record Failure(String errorMessage) implements ReservationResult {}

    static ReservationResult success(
        PendingReservation reservation, List<AllocationRule> rules, int shardId) {
      return new Success(reservation, rules, shardId);
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
    AVAILABLE(3), // Plenty of capacity
    LOW_CAPACITY(2), // Running low on capacity
    EXHAUSTED(1), // No available capacity, but pending reservations might free up
    FULLY_ALLOCATED(0); // Completely allocated with no pending reservations

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
  public CompletionStage<ReservationResult> reserveCapacity(String userId, String reservationId) {
    // Determine primary shard based on userId
    int primaryShard = selectShardForUser(userId);
    logger.debug("Primary shard for user [{}] is [{}]", userId, primaryShard);

    // If primary shard is not fully allocated, try it first
    if (shardStatuses.get(primaryShard) != ShardStatus.FULLY_ALLOCATED) {
      return tryReservationOnShard(primaryShard, userId, reservationId)
          .thenCompose(
              result -> {
                if (result.isSuccess()) {
                  // Asynchronously fetch status of some other random shard to keep statuses updated
                  refreshRandomShardStatusAsync();
                  return CompletableFuture.completedStage(result);
                }
                // Primary shard failed, try fallback
                return tryFallbackShards(userId, reservationId, new BitSet(numShards));
              });
    } else {
      // Primary shard is fully allocated, go directly to fallback
      BitSet triedShards = new BitSet(numShards);
      triedShards.set(primaryShard); // Mark primary shard as already tried
      return tryFallbackShards(userId, reservationId, triedShards);
    }
  }

  /**
   * Attempt to reserve capacity using fallback shards, exhausting all available options. Will try
   * until all shards have been attempted or capacity is found.
   */
  private CompletionStage<ReservationResult> tryFallbackShards(
      String userId, String reservationId, BitSet triedShards) {

    // If we've tried all shards, give up
    if (triedShards.cardinality() >= numShards) {
      return CompletableFuture.completedStage(
          ReservationResult.failure("No capacity available in any shard"));
    }

    // Use power of two choices for next attempt
    return tryPowerOfTwoChoices(userId, reservationId, triedShards)
        .thenCompose(
            result -> {
              if (result.isSuccess()) {
                return CompletableFuture.completedStage(result);
              }

              // Update tried shards from latest attempt - recursive call will try remaining shards
              return tryFallbackShards(userId, reservationId, triedShards);
            });
  }

  /**
   * Power of Two Choices algorithm for load balancing: 1. Choose two random shards (excluding
   * already tried ones and fully allocated ones); 2. Check capacity status of both; 3. Use the one
   * with more available capacity.
   */
  private CompletionStage<ReservationResult> tryPowerOfTwoChoices(
      String userId, String reservationId, BitSet triedShards) {

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
    ShardStatus firstStatus = shardStatuses.get(firstChoice);
    ShardStatus secondStatus = shardStatuses.get(secondChoice);

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
    return tryReservationOnShard(selectedShard, userId, reservationId)
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
              if (shardStatuses.get(alternateShard) == ShardStatus.FULLY_ALLOCATED) {
                // This will continue with tryFallbackShards since both were marked as tried
                return CompletableFuture.completedStage(
                    ReservationResult.failure("Both candidate shards unavailable"));
              }

              return tryReservationOnShard(alternateShard, userId, reservationId);
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
      if (!triedShards.get(i) && shardStatuses.get(i) != ShardStatus.FULLY_ALLOCATED) {
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
      int shardId, String userId, String reservationId) {

    String shardEntityId = CapacityShardEntity.formatEntityId(poolId, shardId);
    var command = new CapacityShardEntity.ReserveCapacityCommand(reservationId, userId);

    return componentClient
        .forEventSourcedEntity(shardEntityId)
        .method(CapacityShardEntity::reserveCapacity)
        .invokeAsync(command)
        .thenApply(
            reservationResponse -> {
              // Update shard status based on the response
              updateShardStatus(shardId, reservationResponse.capacityStatus());

              return ReservationResult.success(
                  reservationResponse.reservation(), allocationRules, shardId);
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
    double usageRatio = 1.0 - (double) status.availableCapacity() / status.totalCapacity();

    ShardStatus newStatus;
    if (status.availableCapacity() <= 0 && status.reservedCapacity() <= 0) {
      newStatus = ShardStatus.FULLY_ALLOCATED;
    } else if (status.availableCapacity() <= 0) {
      newStatus = ShardStatus.EXHAUSTED;
    } else if (usageRatio > 0.8) { // More than 80% used
      newStatus = ShardStatus.LOW_CAPACITY;
    } else {
      newStatus = ShardStatus.AVAILABLE;
    }

    ShardStatus oldStatus = shardStatuses.getAndSet(shardId, newStatus);
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

  /** Get allocation rules for this pool */
  public List<AllocationRule> getAllocationRules() {
    return Collections.unmodifiableList(allocationRules);
  }

  /** Get the pool ID */
  public String getPoolId() {
    return poolId;
  }

  /** Get the number of shards */
  public int getNumShards() {
    return numShards;
  }
}
