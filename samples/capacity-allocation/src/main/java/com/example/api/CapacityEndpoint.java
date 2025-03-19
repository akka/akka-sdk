package com.example.api;

import akka.Done;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import akka.javasdk.timer.TimerScheduler;
import com.example.application.CapacityPoolEntity;
import com.example.application.CapacityShardClient;
import com.example.application.CapacityShardClientProvider;
import com.example.application.CapacityShardEntity;
import com.example.application.UserActivityEntity;
import com.example.domain.AllocationRule;
import com.example.domain.CapacityPool;
import com.example.domain.CapacityShard;
import com.example.domain.UserActivity;
import com.typesafe.config.Config;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/capacity")
public class CapacityEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(CapacityEndpoint.class);

  private final ComponentClient componentClient;
  private final CapacityShardClientProvider shardClientProvider;
  private final TimerScheduler timerScheduler;

  private final Optional<Duration> reservationTimeout;

  public CapacityEndpoint(
      ComponentClient componentClient,
      CapacityShardClientProvider shardClientProvider,
      TimerScheduler timerScheduler,
      Config config) {
    this.componentClient = componentClient;
    this.shardClientProvider = shardClientProvider;
    this.timerScheduler = timerScheduler;
    this.reservationTimeout = optionalDuration(config, "capacity.reservation.timeout");
  }

  private static Optional<Duration> optionalDuration(Config config, String path) {
    return config.hasPath(path) ? Optional.of(config.getDuration(path)) : Optional.empty();
  }

  // API requests

  public record CreatePoolRequest(
      String name,
      String description,
      int totalCapacity,
      int numShards,
      List<AllocationRule> allocationRules) {

    public CreatePoolRequest {
      if (name == null) name = "";
      if (description == null) description = "";
      if (allocationRules == null) allocationRules = List.of();
    }
  }

  public record ReservationRequest(String userId, String requestId) {}

  // API responses

  public enum ReservationStatus {
    ACCEPTED,
    CONFIRMED,
    REJECTED,
    CANCELLED;
  }

  public record ReservationResponse(ReservationStatus status, String requestId, String message) {
    public static ReservationResponse accepted(String requestId, String message) {
      return new ReservationResponse(ReservationStatus.ACCEPTED, requestId, message);
    }

    public static ReservationResponse confirmed(String requestId, String message) {
      return new ReservationResponse(ReservationStatus.CONFIRMED, requestId, message);
    }

    public static ReservationResponse rejected(String requestId, String message) {
      return new ReservationResponse(ReservationStatus.REJECTED, requestId, message);
    }

    public static ReservationResponse cancelled(String requestId, String message) {
      return new ReservationResponse(ReservationStatus.CANCELLED, requestId, message);
    }
  }

  // Endpoint methods

  @Post("/pools/{poolId}/create")
  public CompletionStage<HttpResponse> createPool(String poolId, CreatePoolRequest request) {
    logger.info("Creating capacity pool [{}] with id [{}]", request.name(), poolId);

    var createPoolCommand =
        new CapacityPool.CreatePool(
            poolId,
            request.name(),
            request.description(),
            request.totalCapacity(),
            request.numShards(),
            request.allocationRules());

    return componentClient
        .forEventSourcedEntity(poolId)
        .method(CapacityPoolEntity::createPool)
        .invokeAsync(createPoolCommand)
        .thenCompose(poolCreated -> initializeShards(poolId, request))
        .thenApply(
            result -> {
              logger.debug("Capacity pool created successfully: {}", poolId);
              return HttpResponses.created(poolId);
            })
        .exceptionally(
            error -> {
              throw HttpException.badRequest(error.getMessage());
            });
  }

  private CompletionStage<Done> initializeShards(String poolId, CreatePoolRequest request) {
    int numShards = request.numShards();
    int totalCapacity = request.totalCapacity();

    logger.debug(
        "Initializing [{}] shards for pool [{}] with total capacity [{}]",
        numShards,
        poolId,
        totalCapacity);

    int shardsPerInstance = totalCapacity / numShards;
    int remainder = totalCapacity % numShards;
    List<CompletionStage<Done>> shardInitializations = new ArrayList<>();

    for (int i = 0; i < numShards; i++) {
      int shardId = i;
      // Distribute remainder across shards if capacity doesn't divide evenly
      int shardCapacity = shardsPerInstance + (i < remainder ? 1 : 0);
      String shardEntityId = CapacityShardEntity.formatEntityId(poolId, shardId);

      var initCommand =
          new CapacityShard.InitializeShard(poolId, shardId, shardCapacity, numShards);

      CompletionStage<Done> shardInitFuture =
          componentClient
              .forEventSourcedEntity(shardEntityId)
              .method(CapacityShardEntity::initializeShard)
              .invokeAsync(initCommand);

      shardInitializations.add(shardInitFuture);
    }

    // Wait for all shards to be initialized
    return CompletableFuture.allOf(
            shardInitializations.stream()
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new))
        .thenApply(__ -> Done.getInstance());
  }

  @Get("/pools/{poolId}")
  public CompletionStage<CapacityPool> getPool(String poolId) {
    logger.debug("Getting capacity pool [{}]", poolId);

    return componentClient
        .forEventSourcedEntity(poolId)
        .method(CapacityPoolEntity::getPoolStatus)
        .invokeAsync()
        .exceptionally(
            error -> {
              logger.warn("Failed to get capacity pool [{}]: {}", poolId, error.getMessage());
              throw HttpException.notFound();
            });
  }

  @Post("/pools/{poolId}/reservations")
  public CompletionStage<ReservationResponse> reserve(String poolId, ReservationRequest request) {

    // Use client-provided requestId or generate one if not provided
    final String requestId =
        request.requestId() != null && !request.requestId().isEmpty()
            ? request.requestId()
            : UUID.randomUUID().toString();

    logger.debug(
        "Processing reservation request [{}] in pool [{}] for user [{}]",
        requestId,
        poolId,
        request.userId());

    return shardClientProvider
        .getClientForPool(poolId)
        .thenCompose(client -> requestAllocation(client, poolId, request.userId(), requestId))
        .exceptionally(
            error -> {
              logger.warn(
                  "Failed to process reservation request [{}] in pool [{}]: {}",
                  requestId,
                  poolId,
                  error.getMessage());
              return ReservationResponse.rejected(
                  requestId, "Invalid request: " + error.getMessage());
            });
  }

  private CompletionStage<ReservationResponse> requestAllocation(
      CapacityShardClient client, String poolId, String userId, String requestId) {
    List<AllocationRule> rules = client.getAllocationRules();
    String userActivityId = UserActivityEntity.formatEntityId(poolId, userId);
    String timerId = String.format("reservation-timeout:%s:%s:%s", poolId, userId, requestId);

    CompletionStage<Done> timerRegistration;

    if (reservationTimeout.isPresent()) {
      logger.debug(
          "Setting reservation timeout of [{}] for user [{}] request [{}]",
          reservationTimeout.get(),
          userId,
          requestId);

      var cancelCommand =
          new UserActivity.CancelAllocation(requestId, "Reservation timeout exceeded");

      timerRegistration =
          timerScheduler.startSingleTimer(
              timerId,
              reservationTimeout.get(),
              componentClient
                  .forEventSourcedEntity(userActivityId)
                  .method(UserActivityEntity::cancelAllocation)
                  .deferred(cancelCommand));
    } else {
      timerRegistration = CompletableFuture.completedStage(Done.getInstance());
    }

    var requestCommand = new UserActivity.RequestAllocation(requestId, rules);

    return timerRegistration.thenCompose(
        __ ->
            componentClient
                .forEventSourcedEntity(userActivityId)
                .method(UserActivityEntity::requestAllocation)
                .invokeAsync(requestCommand)
                .thenCompose(
                    result -> handleValidationResult(client, result, poolId, userId, requestId)));
  }

  private CompletionStage<ReservationResponse> handleValidationResult(
      CapacityShardClient client,
      UserActivity.ValidationResult validationResult,
      String poolId,
      String userId,
      String requestId) {

    return switch (validationResult) {
      case UserActivity.ValidationResult.Accepted accepted -> {
        logger.debug(
            "Request [{}] accepted for user [{}], proceeding to capacity reservation",
            requestId,
            userId);

        yield reserveCapacity(client, poolId, userId, requestId);
      }

      case UserActivity.ValidationResult.Confirmed confirmed -> {
        logger.debug("Request [{}] was already confirmed", requestId);

        yield CompletableFuture.completedStage(
            ReservationResponse.confirmed(requestId, "Reservation already confirmed"));
      }

      case UserActivity.ValidationResult.Rejected rejected -> {
        logger.debug("Request [{}] was rejected: {}", requestId, rejected.reason());

        yield CompletableFuture.completedStage(
            ReservationResponse.rejected(requestId, rejected.reason()));
      }
    };
  }

  private CompletionStage<ReservationResponse> reserveCapacity(
      CapacityShardClient client, String poolId, String userId, String requestId) {

    return client
        .reserveCapacity(userId, requestId)
        .thenCompose(
            result ->
                switch (result) {
                  case CapacityShardClient.ReservationResult.Success success -> {
                    String userActivityId = UserActivityEntity.formatEntityId(poolId, userId);
                    var confirmCommand = new UserActivity.ConfirmAllocation(requestId);

                    yield componentClient
                        .forEventSourcedEntity(userActivityId)
                        .method(UserActivityEntity::confirmAllocation)
                        .invokeAsync(confirmCommand)
                        .thenApply(
                            __ ->
                                ReservationResponse.confirmed(requestId, "Reservation confirmed"));
                  }

                  case CapacityShardClient.ReservationResult.Failure failure -> {
                    String userActivityId = UserActivityEntity.formatEntityId(poolId, userId);

                    var rejectCommand =
                        new UserActivity.RejectAllocation(
                            requestId, "Failed to reserve capacity: " + failure.errorMessage());

                    yield componentClient
                        .forEventSourcedEntity(userActivityId)
                        .method(UserActivityEntity::rejectAllocation)
                        .invokeAsync(rejectCommand)
                        .thenApply(
                            __ ->
                                ReservationResponse.rejected(
                                    requestId,
                                    "Failed to reserve capacity: " + failure.errorMessage()));
                  }
                });
  }

  /** Get reservation status for a specific request */
  @Get("/pools/{poolId}/reservations/{userId}/{requestId}")
  public CompletionStage<HttpResponse> getReservationStatus(
      String poolId, String userId, String requestId) {

    String userActivityId = UserActivityEntity.formatEntityId(poolId, userId);

    return componentClient
        .forEventSourcedEntity(userActivityId)
        .method(UserActivityEntity::getAllocationHistory)
        .invokeAsync()
        .thenApply(
            activity -> {
              var existingAllocation = activity.getRequestStatus(requestId);

              if (existingAllocation.isEmpty()) {
                return HttpResponses.notFound();
              }

              var allocation = existingAllocation.get();
              var response =
                  switch (allocation.status()) {
                    case ACCEPTED ->
                        ReservationResponse.accepted(
                            allocation.requestId(),
                            "Request accepted, waiting for capacity confirmation");

                    case CONFIRMED ->
                        ReservationResponse.confirmed(
                            allocation.requestId(), "Capacity reservation confirmed");

                    case REJECTED ->
                        ReservationResponse.rejected(
                            allocation.requestId(),
                            allocation.statusReason().orElse("Request rejected"));

                    case CANCELLED ->
                        ReservationResponse.cancelled(
                            allocation.requestId(),
                            allocation.statusReason().orElse("Reservation cancelled"));
                  };

              return HttpResponses.ok(response);
            })
        .exceptionally(
            error -> {
              throw HttpException.badRequest(error.getMessage());
            });
  }
}
