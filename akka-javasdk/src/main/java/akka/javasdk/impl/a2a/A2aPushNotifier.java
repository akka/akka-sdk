/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.javasdk.JsonSupport;
import akka.javasdk.a2a.A2aStreamResponse;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.client.ComponentClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
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
   * Starts one delivery of the task per push notification configuration. A failure to start a
   * delivery is logged and does not fail the change of the task.
   */
  public static void taskChanged(ComponentClient componentClient, A2aTaskEntity.State changed) {
    List<A2aTaskPushNotificationConfig> configs;
    try {
      configs =
          componentClient
              .forKeyValueEntity(changed.task().id())
              .method(A2aTaskExtrasEntity::get)
              .invoke()
              .pushConfigs();
    } catch (RuntimeException e) {
      log.warn("Failed to read the push notification configurations of A2A task", e);
      return;
    }
    if (configs.isEmpty()) return;
    var body = body(changed.task());
    for (var config : configs) {
      // One delivery per task version and configuration, so a repeated change starts none again
      var deliveryId =
          UUID.nameUUIDFromBytes(
                  (changed.task().id() + "\n" + changed.version() + "\n" + config.id())
                      .getBytes(StandardCharsets.UTF_8))
              .toString();
      var delivery =
          new A2aPushDeliveryWorkflow.State(
              config.url(), config.token(), authorization(config), body, 0);
      try {
        componentClient
            .forWorkflow(deliveryId)
            .method(A2aPushDeliveryWorkflow::start)
            .invoke(delivery);
      } catch (RuntimeException e) {
        log.warn("Failed to start a push notification of A2A task [{}]", changed.task().id(), e);
      }
    }
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
