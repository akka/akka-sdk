package store.order.api;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.keyvalueentity.KeyValueEntity;
import store.order.domain.Order;

import java.time.Instant;

@ComponentId("order")
public class OrderEntity extends KeyValueEntity<Order> {

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
