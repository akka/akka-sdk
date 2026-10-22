package com.example.multiregion;

// tag::entity[]
import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "multi-region-order")
public class OrderEntity extends EventSourcedEntity<OrderPlaced, OrderEvent> {

  public Effect<Done> place(long amountInCents) {
    if (currentState() != null) {
      if (currentState().amountInCents() != amountInCents) {
        return effects().error("Order already placed with a different amount");
      }
      return effects().reply(Done.getInstance());
    }
    if (amountInCents <= 0) {
      return effects().error("Amount must be positive");
    }
    var orderId = commandContext().entityId();
    var event = new OrderPlaced(orderId, "order-payment/" + orderId, amountInCents);
    return effects().persist(event).thenReply(__ -> Done.getInstance());
  }

  @Override
  public OrderPlaced applyEvent(OrderEvent event) {
    return switch (event) {
      case OrderPlaced placed -> placed;
    };
  }
}
// end::entity[]
