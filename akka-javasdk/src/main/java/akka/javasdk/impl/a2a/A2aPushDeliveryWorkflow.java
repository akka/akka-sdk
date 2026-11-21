/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.Done;
import akka.actor.ActorSystem;
import akka.annotation.InternalApi;
import akka.http.javadsl.ClientTransport;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.headers.RawHeader;
import akka.http.javadsl.settings.ConnectionPoolSettings;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;
import akka.stream.Materializer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTERNAL API
 *
 * <p>Delivers one push notification of an A2A task to one webhook. The step posts the notification;
 * when the webhook does not answer with a 2xx status, the workflow pauses for a backoff that
 * doubles with each attempt and posts again when the pause ends. The workflow deletes itself when
 * the notification is delivered or the attempts are used up.
 *
 * <p>A workflow per delivery keeps the attempt count and the backoff durable across restarts, and
 * keeps a webhook that fails from delaying the notifications of other tasks.
 */
@InternalApi
@Component(id = "akka-a2a-push-delivery")
public final class A2aPushDeliveryWorkflow extends Workflow<A2aPushDeliveryWorkflow.State> {

  private static final Logger log = LoggerFactory.getLogger(A2aPushDeliveryWorkflow.class);

  static final int MAX_ATTEMPTS = 8;
  static final Duration FIRST_BACKOFF = Duration.ofSeconds(1);
  static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
  static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

  /**
   * @param url the webhook
   * @param token the token of the configuration, sent in {@code X-A2A-Notification-Token}, or null
   * @param authorization the value of the {@code Authorization} header, or null
   * @param body the {@code StreamResponse} JSON to post
   * @param attempt the number of failed attempts so far
   */
  public record State(String url, String token, String authorization, String body, int attempt) {}

  private final ActorSystem system;
  private final A2aPushUrlPolicy urlPolicy;

  public A2aPushDeliveryWorkflow(Materializer materializer, A2aPushUrlPolicy urlPolicy) {
    this.system = materializer.system();
    this.urlPolicy = urlPolicy;
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder().defaultStepTimeout(REQUEST_TIMEOUT.plusSeconds(5)).build();
  }

  /** Starts the delivery. A delivery that has already started is left as it is. */
  public Effect<Done> start(State delivery) {
    if (currentState() != null) return effects().reply(Done.getInstance());
    return effects()
        .updateState(delivery)
        .transitionTo(A2aPushDeliveryWorkflow::deliver)
        .thenReply(Done.getInstance());
  }

  /** Called when the backoff after a failed attempt ends. */
  public Effect<Done> retry() {
    if (currentState() == null) return effects().error("No push notification to deliver");
    return effects().transitionTo(A2aPushDeliveryWorkflow::deliver).thenReply(Done.getInstance());
  }

  private StepEffect deliver() {
    var delivery = currentState();
    // The host is resolved again, because it can resolve to another address than when the
    // configuration was stored
    var check = urlPolicy.check(delivery.url());
    if (check.refusal() != null) {
      log.warn("Push notification to [{}] not sent: {}", delivery.url(), check.refusal());
      return stepEffects().thenDelete();
    }
    if (post(system, delivery, check.addresses())) return stepEffects().thenDelete();
    var attempts = delivery.attempt() + 1;
    if (attempts >= MAX_ATTEMPTS) {
      log.warn(
          "Giving up on a push notification to [{}] after {} attempts", delivery.url(), attempts);
      return stepEffects().thenDelete();
    }
    return stepEffects()
        .updateState(
            new State(
                delivery.url(),
                delivery.token(),
                delivery.authorization(),
                delivery.body(),
                attempts))
        .thenPause(pauseSetting(backoff(attempts)).timeoutHandler(A2aPushDeliveryWorkflow::retry));
  }

  static Duration backoff(int attempts) {
    var backoff = FIRST_BACKOFF.multipliedBy(1L << Math.min(attempts - 1, 20));
    return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
  }

  /**
   * Posts the notification, and connects to one of the checked addresses when there are any. The
   * request keeps the host name of the URL, so the {@code Host} header, TLS server name indication
   * and certificate verification use the name and not the address.
   *
   * @param addresses the addresses that the URL policy checked, or empty to resolve the host when
   *     connecting
   * @return true when the webhook answered with a 2xx status
   */
  static boolean post(ActorSystem system, State delivery, List<InetAddress> addresses) {
    try {
      var request =
          HttpRequest.POST(delivery.url())
              .withEntity(
                  ContentTypes.APPLICATION_JSON, delivery.body().getBytes(StandardCharsets.UTF_8));
      if (delivery.token() != null && !delivery.token().isBlank()) {
        request =
            request.addHeader(RawHeader.create(A2aPushNotifier.TOKEN_HEADER, delivery.token()));
      }
      if (delivery.authorization() != null) {
        request = request.addHeader(RawHeader.create("Authorization", delivery.authorization()));
      }
      var http = Http.get(system);
      var pool = ConnectionPoolSettings.create(system);
      if (!addresses.isEmpty()) {
        var pinned = addresses.getFirst();
        pool =
            pool.withTransport(
                ClientTransport.withCustomResolver(
                    (host, port) ->
                        CompletableFuture.completedFuture(
                            new InetSocketAddress(pinned, (Integer) port))));
      }
      var response =
          http.singleRequest(request, http.defaultClientHttpsContext(), pool, system.log())
              .toCompletableFuture()
              .get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      response.discardEntityBytes(system);
      if (response.status().isSuccess()) return true;
      log.debug(
          "Push notification to [{}] answered {}", delivery.url(), response.status().intValue());
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      log.debug("Push notification to [{}] failed", delivery.url(), e);
      return false;
    }
  }
}
