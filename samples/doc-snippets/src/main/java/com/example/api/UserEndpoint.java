package com.example.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.example.application.UserEntityWithNotifications;
import com.example.application.UserEntityWithNotifications.UserNotification;

// tag::entity-notification[]
@HttpEndpoint("/user")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class UserEndpoint {

  public record UserUpdate(String type, String name, String email) {} // <1>

  // end::entity-notification[]
  private final ComponentClient componentClient;

  public UserEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::entity-notification[]
  @Get("/updates/{userId}")
  public HttpResponse updates(String userId) {
    var source = componentClient
      .forKeyValueEntity(userId)
      .notificationStream(UserEntityWithNotifications::updates)
      .source()
      .map(notification -> toApi(notification)); // <2>
    return HttpResponses.serverSentEvents(source);
  }

  private UserUpdate toApi(UserNotification notification) {
    return switch (notification) {
      case UserNotification.UserUpdated updated -> new UserUpdate(
        "user-updated",
        updated.name(),
        updated.email()
      );
    };
  }
}
// end::entity-notification[]
