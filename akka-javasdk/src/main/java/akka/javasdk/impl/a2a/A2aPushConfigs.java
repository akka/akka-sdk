/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.javasdk.a2a.A2aListTaskPushNotificationConfigsResponse;
import akka.javasdk.a2a.A2aTaskPushNotificationConfig;
import akka.javasdk.client.ComponentClient;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * INTERNAL API
 *
 * <p>The push notification configurations of A2A tasks, stored in {@link A2aTaskExtrasEntity} under
 * the task id. The caller checks that the task exists and that the caller may see it.
 */
@InternalApi
public final class A2aPushConfigs {

  /** The most configurations one task can have. */
  static final int MAX_PER_TASK = 10;

  static final int DEFAULT_PAGE_SIZE = 50;

  private final ComponentClient componentClient;

  public A2aPushConfigs(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  /**
   * Stores a configuration for a task. A configuration without an id gets a new one, and one with
   * the id of an existing configuration replaces it.
   *
   * @throws A2aException INVALID_PARAMS when the URL is not an http or https URL
   */
  public A2aTaskPushNotificationConfig store(String taskId, A2aTaskPushNotificationConfig config) {
    checkUrl(config.url());
    var id =
        config.id() == null || config.id().isBlank() ? UUID.randomUUID().toString() : config.id();
    var existing = configs(taskId);
    if (existing.size() >= MAX_PER_TASK && existing.stream().noneMatch(c -> c.id().equals(id))) {
      throw new A2aException(
          A2aErrorCode.INVALID_PARAMS,
          "Task [" + taskId + "] has " + MAX_PER_TASK + " push notification configurations");
    }
    var stored =
        new A2aTaskPushNotificationConfig(
            config.tenant(), id, taskId, config.url(), config.token(), config.authentication());
    return componentClient
        .forKeyValueEntity(taskId)
        .method(A2aTaskExtrasEntity::putPushConfig)
        .invoke(stored);
  }

  /**
   * @throws A2aException TASK_NOT_FOUND when the task has no configuration with this id
   */
  public A2aTaskPushNotificationConfig get(String taskId, String configId) {
    return configs(taskId).stream()
        .filter(c -> c.id().equals(configId))
        .findFirst()
        .orElseThrow(
            () ->
                new A2aException(
                    A2aErrorCode.TASK_NOT_FOUND,
                    "Push notification configuration ["
                        + configId
                        + "] of task ["
                        + taskId
                        + "] not found"));
  }

  /**
   * One page of the configurations of a task. The page token is the offset of the page.
   *
   * @throws A2aException INVALID_PARAMS when the page size or page token is invalid
   */
  public A2aListTaskPushNotificationConfigsResponse list(
      String taskId, Integer pageSize, String pageToken) {
    if (pageSize != null && pageSize < 0) {
      throw new A2aException(A2aErrorCode.INVALID_PARAMS, "pageSize must not be negative");
    }
    int size = pageSize == null || pageSize == 0 ? DEFAULT_PAGE_SIZE : pageSize;
    int offset;
    try {
      offset = pageToken == null || pageToken.isBlank() ? 0 : Integer.parseInt(pageToken);
    } catch (NumberFormatException e) {
      offset = -1;
    }
    if (offset < 0) throw new A2aException(A2aErrorCode.INVALID_PARAMS, "Invalid pageToken");
    var all = configs(taskId);
    var from = Math.min(offset, all.size());
    var to = Math.min(from + size, all.size());
    return new A2aListTaskPushNotificationConfigsResponse(
        all.subList(from, to), to < all.size() ? String.valueOf(to) : null);
  }

  public void delete(String taskId, String configId) {
    componentClient
        .forKeyValueEntity(taskId)
        .method(A2aTaskExtrasEntity::deletePushConfig)
        .invoke(configId);
  }

  private List<A2aTaskPushNotificationConfig> configs(String taskId) {
    return componentClient
        .forKeyValueEntity(taskId)
        .method(A2aTaskExtrasEntity::get)
        .invoke()
        .pushConfigs();
  }

  private static void checkUrl(String url) {
    if (url == null || url.isBlank()) {
      throw new A2aException(
          A2aErrorCode.INVALID_PARAMS, "The push notification configuration has no url");
    }
    try {
      var uri = URI.create(url);
      var scheme = uri.getScheme();
      if (uri.getHost() == null || !("https".equals(scheme) || "http".equals(scheme))) {
        throw new IllegalArgumentException();
      }
    } catch (IllegalArgumentException e) {
      throw new A2aException(
          A2aErrorCode.INVALID_PARAMS,
          "The push notification url must be an absolute http or https URL");
    }
  }
}
