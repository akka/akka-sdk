package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import com.example.api.CapacityEndpoint.CreatePoolRequest;
import com.example.api.CapacityEndpoint.ReservationRequest;
import com.example.api.CapacityEndpoint.ReservationResponse;
import com.example.api.CapacityEndpoint.ReservationStatus;
import com.example.application.UserActivityView;
import com.example.application.UserActivityView.AllocationStatus;
import com.example.domain.AllocationRule;
import com.example.domain.CapacityPool;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;

public class CapacityAllocationIntegrationTest extends TestKitSupport {

  private final Duration requestTimeout = Duration.ofSeconds(10);

  private final Duration viewTimeout = Duration.ofSeconds(20);
  private final Duration viewPollDelay = Duration.ofSeconds(1);
  private final Duration viewPollInterval = Duration.ofSeconds(1);

  private final ConditionFactory awaitViews =
      Awaitility.with()
          .pollDelay(viewPollDelay)
          .and()
          .pollInterval(viewPollInterval)
          .await()
          .atMost(viewTimeout)
          .ignoreExceptions()
          .await();

  @Test
  public void createCapacityPoolAndReserve() {
    String poolName = "test-pool-" + UUID.randomUUID().toString().substring(0, 8);
    String userId = "test-user-" + UUID.randomUUID().toString().substring(0, 8);
    String requestId = UUID.randomUUID().toString();

    String poolId =
        createCapacityPool(
            new CreatePoolRequest(
                poolName,
                "Test capacity pool",
                10,
                2,
                List.of(new AllocationRule.MaxPerUserRule("max-user-rule", 3, null))));

    CapacityPool pool = getPool(poolId);
    assertThat(pool.name()).isEqualTo(poolName);
    assertThat(pool.totalCapacity()).isEqualTo(10);
    assertThat(pool.numShards()).isEqualTo(2);

    ReservationResponse reservationResponse = reserve(poolId, userId, requestId);

    assertThat(reservationResponse.status()).isEqualTo(ReservationStatus.CONFIRMED);
    assertThat(reservationResponse.requestId()).isEqualTo(requestId);

    // Views are eventually updated
    awaitViews.untilAsserted(
        () -> {
          UserActivityView.ActivitySummary activity = getUserPoolActivity(userId, poolId);
          assertThat(activity.userId()).isEqualTo(userId);
          assertThat(activity.allocations()).hasSize(1);
          var allocation = findAllocation(activity.allocations(), requestId);
          assertThat(allocation.status()).isEqualTo(AllocationStatus.CONFIRMED);
        });

    ReservationResponse statusResponse = getReservationStatus(poolId, userId, requestId);
    assertThat(statusResponse.status()).isEqualTo(ReservationStatus.CONFIRMED);
  }

  @Test
  public void enforceMaxUserAllocationRule() {
    String poolName = "test-pool-" + UUID.randomUUID().toString().substring(0, 8);
    String userId = "test-user-" + UUID.randomUUID().toString().substring(0, 8);

    String poolId =
        createCapacityPool(
            new CreatePoolRequest(
                poolName,
                "Test capacity pool with limit",
                10,
                1,
                List.of(new AllocationRule.MaxPerUserRule("max-user-rule", 2, null))));

    ReservationResponse response1 = reserve(poolId, userId, "req-1");
    assertThat(response1.status()).isEqualTo(ReservationStatus.CONFIRMED);

    ReservationResponse response2 = reserve(poolId, userId, "req-2");
    assertThat(response2.status()).isEqualTo(ReservationStatus.CONFIRMED);

    // Third reservation should be rejected due to max allocation rule
    ReservationResponse response3 = reserve(poolId, userId, "req-3");
    assertThat(response3.status()).isEqualTo(ReservationStatus.REJECTED);
    assertThat(response3.message()).contains("maximum allocation limit");

    // Views are eventually updated
    awaitViews.untilAsserted(
        () -> {
          UserActivityView.ActivitySummary activity = getUserPoolActivity(userId, poolId);
          assertThat(activity.allocations()).hasSize(3);
          var req1 = findAllocation(activity.allocations(), "req-1");
          var req2 = findAllocation(activity.allocations(), "req-2");
          var req3 = findAllocation(activity.allocations(), "req-3");
          assertThat(req1.status()).isEqualTo(AllocationStatus.CONFIRMED);
          assertThat(req2.status()).isEqualTo(AllocationStatus.CONFIRMED);
          assertThat(req3.status()).isEqualTo(AllocationStatus.REJECTED);
        });
  }

  @Test
  public void exhaustCapacityPool() {
    String poolName = "test-pool-" + UUID.randomUUID().toString().substring(0, 8);

    String poolId =
        createCapacityPool(new CreatePoolRequest(poolName, "Small capacity pool", 2, 1, List.of()));

    ReservationResponse response1 = reserve(poolId, "user-1", "req-1");
    assertThat(response1.status()).isEqualTo(ReservationStatus.CONFIRMED);

    ReservationResponse response2 = reserve(poolId, "user-2", "req-2");
    assertThat(response2.status()).isEqualTo(ReservationStatus.CONFIRMED);

    // Third reservation should be rejected due to capacity exhaustion
    ReservationResponse response3 = reserve(poolId, "user-3", "req-3");
    assertThat(response3.status()).isEqualTo(ReservationStatus.REJECTED);
    assertThat(response3.message()).contains("No capacity available in any shard");
  }

  @Test
  public void multipleShardDistribution() {
    String poolName = "test-pool-" + UUID.randomUUID().toString().substring(0, 8);

    String poolId =
        createCapacityPool(new CreatePoolRequest(poolName, "Multi-shard pool", 10, 5, List.of()));

    for (int i = 0; i < 10; i++) {
      String userId = "dist-user-" + i;
      String requestId = "dist-req-" + i;

      ReservationResponse response = reserve(poolId, userId, requestId);
      assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    // The 11th reservation should fail due to pool exhaustion
    ReservationResponse finalResponse = reserve(poolId, "final-user", "final-req");
    assertThat(finalResponse.status()).isEqualTo(ReservationStatus.REJECTED);
    assertThat(finalResponse.message()).contains("No capacity available in any shard");
  }

  @Test
  public void getUserPoolsAndActivities() {
    String userId = "view-test-user-" + UUID.randomUUID().toString().substring(0, 8);

    String poolId1 =
        createCapacityPool(new CreatePoolRequest("pool-1", "First test pool", 5, 1, List.of()));

    String poolId2 =
        createCapacityPool(new CreatePoolRequest("pool-2", "Second test pool", 5, 1, List.of()));

    reserve(poolId1, userId, "req-1-1");
    reserve(poolId1, userId, "req-1-2");
    reserve(poolId2, userId, "req-2-1");

    awaitViews.untilAsserted(
        () -> {
          UserActivityView.PagedPoolSummaries pools = getUserPools(userId);
          assertThat(pools.pools()).hasSize(2);

          // Check activities for first pool
          UserActivityView.ActivitySummary activity1 = getUserPoolActivity(userId, poolId1);
          assertThat(activity1.allocations()).hasSize(2);

          // Check user's overall activity
          UserActivityView.PagedActivitySummaries activities = getUserActivities(userId);
          assertThat(activities.activities()).hasSize(2); // 2 pools

          int totalAllocations =
              activities.activities().stream().mapToInt(a -> a.allocations().size()).sum();
          assertThat(totalAllocations).isEqualTo(3); // 3 total reservations
        });
  }

  // Helper methods to call API endpoints

  private String createCapacityPool(CreatePoolRequest request) {
    return await(
            httpClient
                .POST("/capacity/pools")
                .withRequestBody(request)
                .responseBodyAs(String.class)
                .invokeAsync(),
            requestTimeout)
        .body();
  }

  private CapacityPool getPool(String poolId) {
    return await(
            httpClient
                .GET("/capacity/pools/" + poolId)
                .responseBodyAs(CapacityPool.class)
                .invokeAsync(),
            requestTimeout)
        .body();
  }

  private ReservationResponse reserve(String poolId, String userId, String requestId) {
    return await(
            httpClient
                .POST("/capacity/pools/" + poolId + "/reservations")
                .withRequestBody(new ReservationRequest(userId, requestId))
                .responseBodyAs(ReservationResponse.class)
                .invokeAsync(),
            requestTimeout)
        .body();
  }

  private ReservationResponse getReservationStatus(String poolId, String userId, String requestId) {
    return await(
            httpClient
                .GET("/capacity/pools/" + poolId + "/reservations/" + userId + "/" + requestId)
                .responseBodyAs(ReservationResponse.class)
                .invokeAsync(),
            requestTimeout)
        .body();
  }

  private UserActivityView.PagedPoolSummaries getUserPools(String userId) {
    return await(
            httpClient
                .GET("/users/" + userId + "/pools")
                .responseBodyAs(UserActivityView.PagedPoolSummaries.class)
                .invokeAsync(),
            requestTimeout)
        .body();
  }

  private UserActivityView.ActivitySummary getUserPoolActivity(String userId, String poolId) {
    return await(
            httpClient
                .GET("/users/" + userId + "/pools/" + poolId + "/activities")
                .responseBodyAs(UserActivityView.ActivitySummary.class)
                .invokeAsync(),
            requestTimeout)
        .body();
  }

  private UserActivityView.PagedActivitySummaries getUserActivities(String userId) {
    return await(
            httpClient
                .GET("/users/" + userId + "/activities")
                .responseBodyAs(UserActivityView.PagedActivitySummaries.class)
                .invokeAsync(),
            requestTimeout)
        .body();
  }

  // Helper methods for assertions

  private UserActivityView.Allocation findAllocation(
      List<UserActivityView.Allocation> allocations, String requestId) {
    return allocations.stream()
        .filter(a -> a.requestId().equals(requestId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Allocation not found for requestId: " + requestId));
  }
}
