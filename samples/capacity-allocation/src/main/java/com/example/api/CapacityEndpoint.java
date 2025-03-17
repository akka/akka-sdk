package com.example.api;

import akka.Done;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import com.example.application.CapacityPoolEntity;
import com.example.application.CapacityShardClient;
import com.example.application.CapacityShardClientProvider;
import com.example.application.CapacityShardClientProvider.ClientInitializationException;
import com.example.application.CapacityShardEntity;
import com.example.application.UserActivityEntity;
import com.example.domain.AllocationRule;
import com.example.domain.CapacityPool;
import com.example.domain.PendingReservation;
import com.example.domain.UserActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/capacity")
public class CapacityEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(CapacityEndpoint.class);
  private final ComponentClient componentClient;
  private final CapacityShardClientProvider shardClientProvider;

  public CapacityEndpoint(
      ComponentClient componentClient, CapacityShardClientProvider shardClientProvider) {
    this.componentClient = componentClient;
    this.shardClientProvider = shardClientProvider;
  }

  // API request messages

  public record CreatePoolRequest(
      String name,
      String description,
      int totalCapacity,
      int numShards,
      List<AllocationRule> allocationRules) {}

  public record ReservationRequest(String userId) {}

  // Endpoint methods

  @Post("/pools")
  public CompletionStage<HttpResponse> createPool(CreatePoolRequest request) {
    String poolId = UUID.randomUUID().toString();
    logger.info("Creating capacity pool with ID: {}", poolId);

    var createPoolCommand =
        new CapacityPoolEntity.CreatePoolCommand(
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
              Throwable cause = unwrapThrowable(error);
              logger.warn("Failed to create capacity pool: {}", cause.getMessage());
              if (cause instanceof IllegalArgumentException) {
                throw HttpException.badRequest(cause.getMessage());
              }
              throw HttpException.error(
                  StatusCodes.INTERNAL_SERVER_ERROR, "Failed to create capacity pool");
            });
  }

  private CompletionStage<Done> initializeShards(String poolId, CreatePoolRequest request) {
    logger.debug("Initializing [{}] shards for pool [{}]", request.numShards(), poolId);

    int shardsPerInstance = request.totalCapacity() / request.numShards();
    int remainder = request.totalCapacity() % request.numShards();

    List<CompletionStage<Done>> shardInitializations = new ArrayList<>();

    for (int i = 0; i < request.numShards(); i++) {
      int shardId = i;
      // Distribute remainder across shards if capacity doesn't divide evenly
      int shardCapacity = shardsPerInstance + (i < remainder ? 1 : 0);
      String shardEntityId = CapacityShardEntity.formatEntityId(poolId, shardId);

      var initCommand =
          new CapacityShardEntity.InitializeShardCommand(poolId, shardId, shardCapacity);

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
        .thenApply(v -> Done.getInstance());
  }

  @Get("/pools/{poolId}")
  public CompletionStage<CapacityPool> getPool(String poolId) {
    logger.debug("Getting capacity pool: {}", poolId);

    return componentClient
        .forEventSourcedEntity(poolId)
        .method(CapacityPoolEntity::getPoolStatus)
        .invokeAsync()
        .exceptionally(
            error -> {
              Throwable cause = unwrapThrowable(error);
              logger.warn("Failed to get capacity pool: {} - {}", poolId, cause.getMessage());
              if (cause instanceof IllegalArgumentException) {
                throw HttpException.notFound();
              }
              throw HttpException.error(
                  StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get capacity pool");
            });
  }

  @Post("/pools/{poolId}/reservations")
  public CompletionStage<HttpResponse> reserveCapacity(String poolId, ReservationRequest request) {
    String reservationId = UUID.randomUUID().toString();
    logger.debug(
        "Attempting to reserve capacity in pool [{}] for user [{}]", poolId, request.userId());

    return shardClientProvider
        .getClientForPool(poolId)
        .thenCompose(client -> client.reserveCapacity(request.userId(), reservationId))
        .thenCompose(
            result ->
                switch (result) {
                  case CapacityShardClient.ReservationResult.Success success -> {
                    // Now validate with UserActivityEntity
                    PendingReservation reservation = success.reservation();
                    int selectedShardId = success.selectedShardId();
                    String userActivityId =
                        UserActivityEntity.formatEntityId(poolId, request.userId());

                    var allocateCommand =
                        new UserActivityEntity.AllocateCommand(
                            reservation.reservationId(),
                            request.userId(),
                            poolId,
                            selectedShardId,
                            success.allocationRules());

                    yield componentClient
                        .forEventSourcedEntity(userActivityId)
                        .method(UserActivityEntity::allocate)
                        .invokeAsync(allocateCommand)
                        .thenApply(
                            validationResult ->
                                switch (validationResult) {
                                  case UserActivity.ValidationResult.Approved __ ->
                                      HttpResponses.created(reservationId);
                                  case UserActivity.ValidationResult.Rejected rejected ->
                                      throw HttpException.badRequest(
                                          "Allocation validation failed: " + rejected.reason());
                                });
                  }

                  case CapacityShardClient.ReservationResult.Failure failure ->
                      CompletableFuture.failedFuture(
                          HttpException.badRequest(
                              "Failed to reserve capacity: " + failure.errorMessage()));
                })
        .exceptionally(
            error -> {
              Throwable cause = unwrapThrowable(error);
              logger.warn(
                  "Failed to reserve capacity in pool [{}]: {}", poolId, cause.getMessage());
              if (cause instanceof IllegalArgumentException) {
                throw HttpException.notFound();
              }
              throw HttpException.badRequest("Failed to reserve capacity: " + cause.getMessage());
            });
  }

  private Throwable unwrapThrowable(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof IllegalArgumentException) {
        return current;
      }
      Throwable cause =
          switch (current) {
            case CompletionException e -> e.getCause();
            case ClientInitializationException e -> e.getCause();
            default -> null;
          };
      if (cause == null) {
        return current;
      }
      current = cause;
    }
    return error;
  }
}
