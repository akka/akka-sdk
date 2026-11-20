/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.http.javadsl.model.HttpEntity;
import akka.javasdk.a2a.A2aDeleteTaskPushNotificationConfigRequest;
import akka.javasdk.a2a.A2aGetTaskPushNotificationConfigRequest;
import akka.javasdk.a2a.A2aListTaskPushNotificationConfigsRequest;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskPushNotificationConfig;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.RequestContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Function;

/**
 * INTERNAL API
 *
 * <p>The four push notification configuration methods of A2A, over JSON-RPC and HTTP+JSON. An
 * endpoint supports them when it publishes a task workflow.
 */
@InternalApi
public final class A2aPushConfigMethods {

  private final A2aPushConfigs configs;
  private final boolean supported;
  private final Function<String, A2aTask> lookup;
  private final ObjectMapper mapper;

  /**
   * @param lookup returns the task the caller may see, or throws TASK_NOT_FOUND
   */
  public A2aPushConfigMethods(
      ComponentClient componentClient,
      boolean supported,
      Function<String, A2aTask> lookup,
      ObjectMapper mapper) {
    this.configs = new A2aPushConfigs(componentClient);
    this.supported = supported;
    this.lookup = lookup;
    this.mapper = mapper;
  }

  /** A JSON-RPC call of one of the four methods. */
  public JsonNode jsonRpc(String method, JsonNode params) {
    checkSupported();
    return switch (method) {
      case A2aMethods.CREATE_TASK_PUSH_NOTIFICATION_CONFIG -> {
        var request = read(params, A2aTaskPushNotificationConfig.class);
        yield mapper.valueToTree(create(request.taskId(), request));
      }
      case A2aMethods.GET_TASK_PUSH_NOTIFICATION_CONFIG -> {
        var request = read(params, A2aGetTaskPushNotificationConfigRequest.class);
        yield mapper.valueToTree(configs.get(task(request.taskId()), require(request.id(), "id")));
      }
      case A2aMethods.LIST_TASK_PUSH_NOTIFICATION_CONFIGS -> {
        var request = read(params, A2aListTaskPushNotificationConfigsRequest.class);
        yield mapper.valueToTree(
            configs.list(task(request.taskId()), request.pageSize(), request.pageToken()));
      }
      case A2aMethods.DELETE_TASK_PUSH_NOTIFICATION_CONFIG -> {
        var request = read(params, A2aDeleteTaskPushNotificationConfigRequest.class);
        configs.delete(task(request.taskId()), require(request.id(), "id"));
        yield mapper.createObjectNode();
      }
      default ->
          throw new A2aException(A2aErrorCode.METHOD_NOT_FOUND, "Method not found: " + method);
    };
  }

  /** An HTTP+JSON call of one of the four methods. */
  public Object http(
      String method,
      String taskId,
      String configId,
      HttpEntity.Strict body,
      RequestContext httpContext) {
    checkSupported();
    return switch (method) {
      case A2aMethods.CREATE_TASK_PUSH_NOTIFICATION_CONFIG -> {
        JsonNode tree;
        try {
          tree = mapper.readTree(body.getData().utf8String());
        } catch (JsonProcessingException e) {
          throw new A2aException(A2aErrorCode.JSON_PARSE_ERROR, "The request is not valid JSON");
        }
        if (tree == null || !tree.isObject()) {
          throw new A2aException(
              A2aErrorCode.INVALID_PARAMS, "The request body is not a JSON object");
        }
        yield create(taskId, read(tree, A2aTaskPushNotificationConfig.class));
      }
      case A2aMethods.GET_TASK_PUSH_NOTIFICATION_CONFIG -> configs.get(task(taskId), configId);
      case A2aMethods.LIST_TASK_PUSH_NOTIFICATION_CONFIGS -> {
        var params = httpContext.queryParams();
        Integer pageSize =
            params
                .getString("pageSize")
                .map(
                    v -> {
                      try {
                        return Integer.valueOf(v);
                      } catch (NumberFormatException e) {
                        throw new A2aException(
                            A2aErrorCode.INVALID_PARAMS, "pageSize must be an integer");
                      }
                    })
                .orElse(null);
        yield configs.list(task(taskId), pageSize, params.getString("pageToken").orElse(null));
      }
      case A2aMethods.DELETE_TASK_PUSH_NOTIFICATION_CONFIG -> {
        configs.delete(task(taskId), configId);
        yield mapper.createObjectNode();
      }
      default ->
          throw new A2aException(A2aErrorCode.METHOD_NOT_FOUND, "Method not found: " + method);
    };
  }

  private A2aTaskPushNotificationConfig create(
      String taskId, A2aTaskPushNotificationConfig request) {
    return configs.store(task(taskId), request);
  }

  // The id of a task the caller may see
  private String task(String taskId) {
    return lookup.apply(require(taskId, "taskId")).id();
  }

  private void checkSupported() {
    if (!supported) {
      throw new A2aException(
          A2aErrorCode.PUSH_NOTIFICATION_NOT_SUPPORTED,
          "This agent does not support push notifications");
    }
  }

  private static String require(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new A2aException(A2aErrorCode.INVALID_PARAMS, "The request has no " + name);
    }
    return value;
  }

  private <T> T read(JsonNode node, Class<T> type) {
    try {
      return mapper.treeToValue(node, type);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      var detail = e instanceof JsonProcessingException j ? j.getOriginalMessage() : e.getMessage();
      throw new A2aException(A2aErrorCode.INVALID_PARAMS, "Invalid parameters: " + detail);
    }
  }
}
