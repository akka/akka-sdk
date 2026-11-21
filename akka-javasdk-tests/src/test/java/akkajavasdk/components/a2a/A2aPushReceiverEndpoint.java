/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.a2a;

import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A webhook for A2A push notifications in tests. It records each notification under the name in its
 * path. A name that starts with {@code fail-once} answers the first notification with 503.
 */
@HttpEndpoint("/a2a-push-receiver")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class A2aPushReceiverEndpoint extends AbstractHttpEndpoint {

  /**
   * @param body the posted JSON
   * @param token the {@code X-A2A-Notification-Token} header, or null
   * @param authorization the {@code Authorization} header, or null
   */
  public record Received(String body, String token, String authorization) {}

  public static final Map<String, List<Received>> received = new ConcurrentHashMap<>();
  public static final Map<String, Integer> attempts = new ConcurrentHashMap<>();

  @Post("/{name}")
  public HttpResponse receive(String name, HttpEntity.Strict body) {
    int attempt = attempts.merge(name, 1, Integer::sum);
    if (name.startsWith("fail-once") && attempt == 1) {
      return HttpResponse.create().withStatus(StatusCodes.SERVICE_UNAVAILABLE);
    }
    var headers = requestContext();
    received
        .computeIfAbsent(name, n -> new CopyOnWriteArrayList<>())
        .add(
            new Received(
                body.getData().utf8String(),
                headers.requestHeader("X-A2A-Notification-Token").map(h -> h.value()).orElse(null),
                headers.requestHeader("Authorization").map(h -> h.value()).orElse(null)));
    return HttpResponses.ok();
  }
}
