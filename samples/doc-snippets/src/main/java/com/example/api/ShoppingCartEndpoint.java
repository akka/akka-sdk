package com.example.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.example.application.ShoppingCartEntityWithNotifications;
import com.example.application.ShoppingCartEntityWithNotifications.CartEvent;

// tag::entity-notification[]
@HttpEndpoint("/cart")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ShoppingCartEndpoint {

  public record CartUpdate(String type, String productId) {} // <1>

  // end::entity-notification[]
  private final ComponentClient componentClient;

  public ShoppingCartEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::entity-notification[]
  @Get("/updates/{cartId}")
  public HttpResponse updates(String cartId) {
    var source = componentClient
      .forEventSourcedEntity(cartId)
      .notificationStream(ShoppingCartEntityWithNotifications::updates)
      .source()
      .map(event -> toApi(event)); // <2>
    return HttpResponses.serverSentEvents(source);
  }

  private CartUpdate toApi(CartEvent event) {
    return switch (event) {
      case CartEvent.ItemAdded added -> new CartUpdate("item-added", added.productId());
    };
  }
}
// end::entity-notification[]
