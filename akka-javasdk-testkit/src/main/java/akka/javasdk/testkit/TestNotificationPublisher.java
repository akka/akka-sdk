/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.NotificationPublisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test implementation of {@link NotificationPublisher} that collects all published notifications
 * for later assertions.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * var notificationPublisher = new TestNotificationPublisher<MyNotification>();
 * var testKit = EventSourcedTestKit.of(entityId, ctx -> new MyEntity(notificationPublisher));
 * }</pre>
 *
 * @param <T> the type of notification messages
 */
public class TestNotificationPublisher<T> implements NotificationPublisher<T> {

  private final List<T> notifications = new ArrayList<>();

  @Override
  public void publish(T msg) {
    notifications.add(msg);
  }

  /** Returns a list of all notifications published so far, in order. */
  public List<T> getNotifications() {
    return Collections.unmodifiableList(notifications);
  }

  /** Clears all collected notifications. */
  public void clear() {
    notifications.clear();
  }
}
