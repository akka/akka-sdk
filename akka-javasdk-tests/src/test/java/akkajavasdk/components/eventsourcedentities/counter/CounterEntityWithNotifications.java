/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.counter;

import akka.javasdk.NotificationPublisher;
import akka.javasdk.NotificationPublisher.NotificationStream;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "counter-entity-with-notifications")
public class CounterEntityWithNotifications extends EventSourcedEntity<Counter, CounterEvent> {

  private final NotificationPublisher<String> notificationPublisher;

  public CounterEntityWithNotifications(NotificationPublisher<String> notificationPublisher) {
    this.notificationPublisher = notificationPublisher;
  }

  @Override
  public Counter emptyState() {
    return new Counter(0);
  }

  public Effect<Integer> increase(int value) {
    return effects()
        .persist(new CounterEvent.ValueIncreased(value))
        .thenReply(
            newState -> {
              notificationPublisher.publish("counter increased to " + newState.value());
              return newState.value();
            });
  }

  public ReadOnlyEffect<Integer> get() {
    return effects().reply(currentState().value());
  }

  public NotificationStream<String> updates() {
    return notificationPublisher.stream();
  }

  @Override
  public Counter applyEvent(CounterEvent event) {
    return currentState().apply(event);
  }
}
