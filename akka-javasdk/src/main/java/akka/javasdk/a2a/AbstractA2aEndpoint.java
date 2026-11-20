/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.annotation.InternalApi;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.impl.a2a.A2aMethods;
import akka.javasdk.impl.a2a.A2aRequestHandler;

/**
 * Base class of an Agent2Agent (A2A) protocol endpoint. Annotate the subclass with {@link
 * akka.javasdk.annotations.a2a.A2aEndpoint} and implement {@link #agents()}.
 *
 * <pre>{@code
 * @A2aEndpoint(name = "Claims", version = "1.0.0")
 * @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
 * public class ClaimsA2aEndpoint extends AbstractA2aEndpoint {
 *   @Override
 *   protected A2aAgents agents() {
 *     return A2aAgents.create().agent(ClaimsAgent.class);
 *   }
 * }
 * }</pre>
 *
 * <p>The base class serves the A2A methods. The public methods annotated with {@link Post} and
 * {@link Get} are those routes, and are not meant to be called directly.
 */
public abstract class AbstractA2aEndpoint {

  private volatile A2aRequestHandler handler;

  /**
   * The agents that this endpoint publishes. The SDK calls it for each request and for the Agent
   * Card.
   */
  protected abstract A2aAgents agents();

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public final void _internalSetHandler(A2aRequestHandler handler) {
    this.handler = handler;
  }

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public final A2aAgents _internalAgents() {
    return agents();
  }

  /**
   * The context of the current request. Available from {@link #agents()} while the endpoint handles
   * a request, not from the constructor.
   */
  protected final A2aRequestContext requestContext() {
    if (handler == null) {
      throw new IllegalStateException(
          "The request context can only be accessed while the endpoint handles a request.");
    }
    return handler.requestContext();
  }

  private A2aRequestHandler handler() {
    if (handler == null) {
      throw new IllegalStateException("The A2A endpoint was not created by the SDK.");
    }
    return handler;
  }

  /** The JSON-RPC binding: every A2A method, sent as a JSON-RPC 2.0 request. */
  @Post
  public final HttpResponse jsonRpc(HttpEntity.Strict body) {
    return handler().jsonRpc(body);
  }

  /** The HTTP+JSON binding of {@code SendMessage}. */
  @Post("/message:send")
  public final HttpResponse sendMessage(HttpEntity.Strict body) {
    return handler().sendMessage(body);
  }

  /** The HTTP+JSON bindings of {@code GetTask} and of {@code GET /tasks/{id}:subscribe}. */
  @Get("/tasks/{taskId}")
  public final HttpResponse getTask(String taskId) {
    return handler().getTask(taskId);
  }

  /** The HTTP+JSON binding of {@code SendStreamingMessage}. */
  @Post("/message:stream")
  public final HttpResponse sendStreamingMessage(HttpEntity.Strict body) {
    return handler().sendStreamingMessage(body);
  }

  /** The HTTP+JSON binding of {@code ListTasks}. */
  @Get("/tasks")
  public final HttpResponse listTasks() {
    return handler().listTasks();
  }

  /**
   * The HTTP+JSON bindings {@code POST /tasks/{id}:cancel} and {@code POST /tasks/{id}:subscribe}.
   * The route has one path variable that holds the task id and the action, because the path matcher
   * takes a variable as a whole segment.
   */
  @Post("/tasks/{taskIdAndAction}")
  public final HttpResponse taskAction(String taskIdAndAction) {
    return handler().taskAction(taskIdAndAction);
  }

  /** The HTTP+JSON binding of {@code CreateTaskPushNotificationConfig}. */
  @Post("/tasks/{taskId}/pushNotificationConfigs")
  public final HttpResponse createTaskPushNotificationConfig(
      String taskId, HttpEntity.Strict body) {
    return handler()
        .pushConfigRoute(A2aMethods.CREATE_TASK_PUSH_NOTIFICATION_CONFIG, taskId, null, body);
  }

  /** The HTTP+JSON binding of {@code ListTaskPushNotificationConfigs}. */
  @Get("/tasks/{taskId}/pushNotificationConfigs")
  public final HttpResponse listTaskPushNotificationConfigs(String taskId) {
    return handler()
        .pushConfigRoute(A2aMethods.LIST_TASK_PUSH_NOTIFICATION_CONFIGS, taskId, null, null);
  }

  /** The HTTP+JSON binding of {@code GetTaskPushNotificationConfig}. */
  @Get("/tasks/{taskId}/pushNotificationConfigs/{configId}")
  public final HttpResponse getTaskPushNotificationConfig(String taskId, String configId) {
    return handler()
        .pushConfigRoute(A2aMethods.GET_TASK_PUSH_NOTIFICATION_CONFIG, taskId, configId, null);
  }

  /** The HTTP+JSON binding of {@code DeleteTaskPushNotificationConfig}. */
  @Delete("/tasks/{taskId}/pushNotificationConfigs/{configId}")
  public final HttpResponse deleteTaskPushNotificationConfig(String taskId, String configId) {
    return handler()
        .pushConfigRoute(A2aMethods.DELETE_TASK_PUSH_NOTIFICATION_CONFIG, taskId, configId, null);
  }

  /** The HTTP+JSON binding of {@code GetExtendedAgentCard}. */
  @Get("/extendedAgentCard")
  public final HttpResponse getExtendedAgentCard() {
    return handler().unsupportedMethod(A2aMethods.GET_EXTENDED_AGENT_CARD);
  }
}
