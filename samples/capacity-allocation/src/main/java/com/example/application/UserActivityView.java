package com.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Table;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.domain.CapacityPoolEvent;
import com.example.domain.UserActivityEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ComponentId("user-activity-view")
public class UserActivityView extends View {

  // Data models

  public record PoolData(String poolId, Optional<String> name, Optional<String> description) {}

  public enum AllocationStatus {
    ACCEPTED,
    CONFIRMED,
    REJECTED,
    CANCELLED
  }

  public record Allocation(
      String requestId, Instant timestamp, AllocationStatus status, String statusReason) {

    public static Allocation accepted(String requestId, Instant timestamp) {
      return new Allocation(requestId, timestamp, AllocationStatus.ACCEPTED, "");
    }

    public static Allocation confirmed(String requestId, Instant timestamp) {
      return new Allocation(requestId, timestamp, AllocationStatus.CONFIRMED, "");
    }

    public static Allocation rejected(String requestId, Instant timestamp, String reason) {
      return new Allocation(requestId, timestamp, AllocationStatus.REJECTED, reason);
    }

    public static Allocation cancelled(String requestId, Instant timestamp, String reason) {
      return new Allocation(requestId, timestamp, AllocationStatus.CANCELLED, reason);
    }
  }

  public record ActivityData(
      String poolId, String userId, List<Allocation> allocations, Instant latestTimestamp) {

    public ActivityData(String poolId, String userId) {
      this(poolId, userId, List.of(), Instant.EPOCH);
    }

    public ActivityData withUpdatedAllocation(Allocation allocation) {
      List<Allocation> updatedAllocations =
          allocations.stream()
              .filter(a -> !a.requestId().equals(allocation.requestId()))
              .collect(Collectors.toCollection(ArrayList::new));
      updatedAllocations.add(allocation);
      updatedAllocations.sort(Comparator.comparing(Allocation::timestamp));
      Instant latestTimestamp = updatedAllocations.get(updatedAllocations.size() - 1).timestamp();
      return new ActivityData(poolId, userId, updatedAllocations, latestTimestamp);
    }
  }

  // Table updaters

  @Table("pools")
  @Consume.FromEventSourcedEntity(CapacityPoolEntity.class)
  public static class PoolsTable extends TableUpdater<PoolData> {

    public Effect<PoolData> onEvent(CapacityPoolEvent event) {
      return switch (event) {
        case CapacityPoolEvent.PoolCreated created ->
            effects()
                .updateRow(
                    new PoolData(
                        created.poolId(),
                        Optional.ofNullable(created.name()),
                        Optional.ofNullable(created.description())));
      };
    }
  }

  @Table("activities")
  @Consume.FromEventSourcedEntity(UserActivityEntity.class)
  public static class ActivitiesTable extends TableUpdater<ActivityData> {

    public Effect<ActivityData> onEvent(UserActivityEvent event) {
      ActivityData currentState =
          rowState() == null ? new ActivityData(event.poolId(), event.userId()) : rowState();

      Allocation allocation =
          switch (event) {
            case UserActivityEvent.RequestAccepted evt ->
                Allocation.accepted(evt.requestId(), evt.timestamp());

            case UserActivityEvent.AllocationConfirmed evt ->
                Allocation.confirmed(evt.requestId(), evt.timestamp());

            case UserActivityEvent.AllocationRejected evt ->
                Allocation.rejected(evt.requestId(), evt.timestamp(), evt.reason());

            case UserActivityEvent.AllocationCancelled evt ->
                Allocation.cancelled(evt.requestId(), evt.timestamp(), evt.reason());
          };

      return effects().updateRow(currentState.withUpdatedAllocation(allocation));
    }
  }

  // View queries

  public record UserAndPoolRequest(String userId, String poolId) {}

  public record UserRequestWithPaging(String userId, int pageOffset, int pageLimit) {}

  public record PoolSummary(String poolId, Optional<String> name, Optional<String> description) {}

  public record PagedPoolSummaries(List<PoolSummary> pools, int totalCount) {}

  public record ActivitySummary(PoolSummary pool, String userId, List<Allocation> allocations) {}

  public record PagedActivitySummaries(List<ActivitySummary> activities, int totalCount) {}

  // Get all capacity pools that a user has had activity in (with pagination)
  @Query(
      """
      SELECT
        collect(pools.*) AS pools,
        total_count() as totalCount
      FROM activities
      LEFT JOIN pools ON activities.poolId = pools.poolId
      WHERE activities.userId = :userId
      ORDER BY pools.name
      OFFSET :pageOffset
      LIMIT :pageLimit
      """)
  public QueryEffect<PagedPoolSummaries> getPools(UserRequestWithPaging request) {
    return queryResult();
  }

  // Get all activity for a user in a particular capacity pool
  @Query(
      """
      SELECT
        pools.* AS pool,
        activities.userId,
        activities.allocations
      FROM activities
      LEFT JOIN pools ON activities.poolId = pools.poolId
      WHERE activities.userId = :userId AND activities.poolId = :poolId
      """)
  public QueryEffect<ActivitySummary> getPoolActivity(UserAndPoolRequest request) {
    return queryResult();
  }

  // Get all activities for a user (with pagination)
  @Query(
      """
      SELECT
        collect(
          pools.* AS pool,
          activities.userId,
          activities.allocations
        ) AS activities,
        total_count() as totalCount
      FROM activities
      LEFT JOIN pools ON activities.poolId = pools.poolId
      WHERE activities.userId = :userId
      ORDER BY activities.latestTimestamp DESC
      OFFSET :pageOffset
      LIMIT :pageLimit
      """)
  public QueryEffect<PagedActivitySummaries> getAllActivities(UserRequestWithPaging request) {
    return queryResult();
  }
}
