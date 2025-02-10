package store.order.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import store.order.domain.Order;

import java.time.Instant;

import static akka.Done.done;

@ComponentId("order")
public class OrderEntity extends KeyValueEntity<Order> {

  public ReadOnlyEffect<Order> get() {
    return effects().reply(currentState());
  }

  public Effect<Done> create(CreateOrder createOrder) {
    Order order =
      new Order(
        commandContext().entityId(),
        createOrder.productId(),
        createOrder.customerId(),
        createOrder.quantity(),
        Instant.now().toEpochMilli());
    return effects().updateState(order).thenReply(done());
  }
}
