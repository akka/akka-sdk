/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * INTERNAL API
 *
 * <p>Receives the push notifications of remote A2A agents for the tasks that this service follows.
 * The path names the subscription, and the {@code X-A2A-Notification-Token} header carries the
 * token registered with the remote agent. A request whose token does not match is refused, so the
 * route is open to every caller: the remote agents run outside the service's project.
 */
@InternalApi
@HttpEndpoint(A2aPushReceiverEndpoint.PATH)
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public final class A2aPushReceiverEndpoint {

  /** The path under which the push notification URLs are built. */
  public static final String PATH = "/a2a-push";

  /** The header that carries the token of the push notification configuration. */
  public static final String TOKEN_HEADER = "X-A2A-Notification-Token";

  private final A2aRemoteTasks remoteTasks;
  private final Optional<String> token;

  /**
   * @param token the value of the {@code X-A2A-Notification-Token} header of the request
   */
  public A2aPushReceiverEndpoint(A2aRemoteTasks remoteTasks, Optional<String> token) {
    this.remoteTasks = remoteTasks;
    this.token = token;
  }

  @Post("/{subscriptionId}")
  public CompletionStage<HttpResponse> push(String subscriptionId, HttpEntity.Strict body) {
    return remoteTasks
        .receivePush(subscriptionId, token, body.getData().toArray())
        .thenApply(status -> HttpResponse.create().withStatus(StatusCodes.get(status)));
  }
}
