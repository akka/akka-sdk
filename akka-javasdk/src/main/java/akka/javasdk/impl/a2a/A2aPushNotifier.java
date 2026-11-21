/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.JsonSupport;
import akka.javasdk.a2a.A2aStreamResponse;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.client.ComponentClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTERNAL API
 *
 * <p>Sends a changed task to each push notification configuration of the task. Each delivery is an
 * {@link A2aPushDeliveryWorkflow}, so that a webhook that fails is retried on its own and does not
 * hold back other deliveries.
 */
@InternalApi
public final class A2aPushNotifier {

  private static final Logger log = LoggerFactory.getLogger(A2aPushNotifier.class);

  /** The header that carries the token of the push notification configuration. */
  static final String TOKEN_HEADER = "X-A2A-Notification-Token";

  private A2aPushNotifier() {}

  /**
   * Starts one delivery of the task per push notification configuration, without blocking. A
   * failure to start a delivery is logged and does not fail the change of the task, so the returned
   * stage always completes normally.
   */
  public static CompletionStage<Done> taskChanged(
      ComponentClient componentClient, A2aTaskEntity.State changed) {
    return componentClient
        .forKeyValueEntity(changed.task().id())
        .method(A2aTaskExtrasEntity::get)
        .invokeAsync()
        .thenCompose(extras -> startDeliveries(componentClient, changed, extras.pushConfigs()))
        .exceptionally(
            e -> {
              log.warn("Failed to read the push notification configurations of A2A task", e);
              return Done.getInstance();
            });
  }

  private static CompletionStage<Done> startDeliveries(
      ComponentClient componentClient,
      A2aTaskEntity.State changed,
      List<A2aTaskPushNotificationConfig> configs) {
    if (configs.isEmpty()) return CompletableFuture.completedFuture(Done.getInstance());
    var body = body(changed.task());
    var started =
        configs.stream()
            .map(
                config -> {
                  // One delivery per task version and configuration, so a repeated change starts
                  // none again
                  var deliveryId =
                      UUID.nameUUIDFromBytes(
                              (changed.task().id() + "\n" + changed.version() + "\n" + config.id())
                                  .getBytes(StandardCharsets.UTF_8))
                          .toString();
                  var delivery =
                      new A2aPushDeliveryWorkflow.State(
                          config.url(), config.token(), authorization(config), body, 0);
                  return componentClient
                      .forWorkflow(deliveryId)
                      .method(A2aPushDeliveryWorkflow::start)
                      .invokeAsync(delivery)
                      .exceptionally(
                          e -> {
                            log.warn(
                                "Failed to start a push notification of A2A task [{}]",
                                changed.task().id(),
                                e);
                            return Done.getInstance();
                          })
                      .toCompletableFuture();
                })
            .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(started).thenApply(v -> Done.getInstance());
  }

  // The full task without its history, which the client reads with GetTask when it needs it
  private static String body(A2aTask task) {
    var withoutHistory =
        new A2aTask(
            task.id(), task.contextId(), task.status(), task.artifacts(), null, task.metadata());
    try {
      return JsonSupport.getObjectMapper().writeValueAsString(A2aStreamResponse.of(withoutHistory));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to write the A2A push notification", e);
    }
  }

  private static String authorization(A2aTaskPushNotificationConfig config) {
    var auth = config.authentication();
    if (auth == null || auth.credentials() == null || auth.credentials().isBlank()) return null;
    var scheme = auth.scheme() == null || auth.scheme().isBlank() ? "Bearer" : auth.scheme();
    return scheme + " " + auth.credentials();
  }
}
