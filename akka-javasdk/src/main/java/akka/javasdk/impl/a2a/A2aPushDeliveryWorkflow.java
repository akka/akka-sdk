/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.Done;
import akka.annotation.InternalApi;
import akka.http.javadsl.model.ContentTypes;
import akka.javasdk.annotations.Component;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.workflow.Workflow;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

  private final HttpClientProvider httpClientProvider;
  private final A2aPushUrlPolicy urlPolicy;

  public A2aPushDeliveryWorkflow(
      HttpClientProvider httpClientProvider, A2aPushUrlPolicy urlPolicy) {
    this.httpClientProvider = httpClientProvider;
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
    var refusal = urlPolicy.refusal(delivery.url());
    if (refusal != null) {
      log.warn("Push notification to [{}] not sent: {}", delivery.url(), refusal);
      return stepEffects().thenDelete();
    }
    if (post(delivery)) return stepEffects().thenDelete();
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

  private boolean post(State delivery) {
    try {
      var uri = URI.create(delivery.url());
      var origin = uri.getScheme() + "://" + uri.getRawAuthority();
      var path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
      if (uri.getRawQuery() != null) path = path + "?" + uri.getRawQuery();
      var request =
          httpClientProvider
              .httpClientFor(origin)
              .POST(path)
              .withTimeout(REQUEST_TIMEOUT)
              .withRequestBody(
                  ContentTypes.APPLICATION_JSON, delivery.body().getBytes(StandardCharsets.UTF_8));
      if (delivery.token() != null && !delivery.token().isBlank()) {
        request = request.addHeader(A2aPushNotifier.TOKEN_HEADER, delivery.token());
      }
      if (delivery.authorization() != null) {
        request = request.addHeader("Authorization", delivery.authorization());
      }
      var status = request.invoke().status();
      if (status.isSuccess()) return true;
      log.debug("Push notification to [{}] answered {}", delivery.url(), status.intValue());
      return false;
    } catch (RuntimeException e) {
      log.debug("Push notification to [{}] failed", delivery.url(), e);
      return false;
    }
  }
}
