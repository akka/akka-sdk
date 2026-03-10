package com.example.application;

import akka.Done;
import akka.javasdk.NotificationPublisher;
import akka.javasdk.NotificationPublisher.NotificationStream;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;

// tag::entity-notification[]
@Component(id = "user-with-notifications")
public class UserEntityWithNotifications
  extends KeyValueEntity<UserEntityWithNotifications.User> {

  public record User(String name, String email) {}

  public sealed interface UserNotification {
    record UserUpdated(String name, String email) implements UserNotification {}
  }

  private final NotificationPublisher<UserNotification> notificationPublisher;

  public UserEntityWithNotifications(
    NotificationPublisher<UserNotification> notificationPublisher
  ) { // <1>
    this.notificationPublisher = notificationPublisher;
  }

  public Effect<Done> createUser(User user) {
    return effects()
      .updateState(user)
      .thenReply(() -> { // <2>
        notificationPublisher.publish(
          new UserNotification.UserUpdated(user.name(), user.email())
        );
        return Done.done();
      });
  }

  public NotificationStream<UserNotification> updates() { // <3>
    return notificationPublisher.stream();
  }
}
// end::entity-notification[]
