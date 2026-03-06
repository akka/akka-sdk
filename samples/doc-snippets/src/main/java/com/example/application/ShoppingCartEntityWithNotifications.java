package com.example.application;

import akka.Done;
import akka.javasdk.NotificationPublisher;
import akka.javasdk.NotificationPublisher.NotificationStream;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import java.util.List;

// tag::entity-notification[]
@Component(id = "shopping-cart-with-notifications")
public class ShoppingCartEntityWithNotifications
  extends EventSourcedEntity<
    ShoppingCartEntityWithNotifications.Cart,
    ShoppingCartEntityWithNotifications.CartEvent
  > {

  public record Cart(String cartId, List<String> items) {}

  public sealed interface CartEvent {
    @TypeName("item-added")
    record ItemAdded(String productId) implements CartEvent {}
  }

  private final String entityId;
  private final NotificationPublisher<CartEvent> notificationPublisher;

  public ShoppingCartEntityWithNotifications(
    EventSourcedEntityContext context,
    NotificationPublisher<CartEvent> notificationPublisher // <1>
  ) {
    this.entityId = context.entityId();
    this.notificationPublisher = notificationPublisher;
  }

  @Override
  public Cart emptyState() {
    return new Cart(entityId, List.of());
  }

  public Effect<Done> addItem(String productId) {
    var event = new CartEvent.ItemAdded(productId);
    return effects()
      .persist(event)
      .thenReply(__ -> {
        notificationPublisher.publish(event); // <2>
        return Done.done();
      });
  }

  public NotificationStream<CartEvent> updates() { // <3>
    return notificationPublisher.stream();
  }

  // end::entity-notification[]

  @Override
  public Cart applyEvent(CartEvent event) {
    return switch (event) {
      case CartEvent.ItemAdded added -> {
        var newItems = new java.util.ArrayList<>(currentState().items());
        newItems.add(added.productId());
        yield new Cart(entityId, List.copyOf(newItems));
      }
    };
  }
  // tag::entity-notification[]
}
// end::entity-notification[]
