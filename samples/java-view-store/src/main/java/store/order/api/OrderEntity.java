package store.order.api;

import akka.platform.javasdk.annotations.TypeId;
import akka.platform.javasdk.valueentity.ValueEntity;
import store.order.domain.Order;

import java.time.Instant;

@TypeId("order")
public class OrderEntity extends ValueEntity<Order> {

  public Effect<Order> get() {
    return effects().reply(currentState());
  }

  public Effect<String> create(CreateOrder createOrder) {
    Order order =
      new Order(
        commandContext().entityId(),
        createOrder.productId(),
        createOrder.customerId(),
        createOrder.quantity(),
        Instant.now().toEpochMilli());
    return effects().updateState(order).thenReply("OK");
  }
}
