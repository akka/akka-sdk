package store.order.application;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import java.time.Instant;
import store.order.domain.Order;

@Component(id = "order")
public class OrderEntity extends KeyValueEntity<Order> {

  public ReadOnlyEffect<Order> get() {
    return effects().reply(currentState());
  }

  public Effect<Done> create(CreateOrder createOrder) {
    Order order = new Order(
      commandContext().entityId(),
      createOrder.productId(),
      createOrder.customerId(),
      createOrder.quantity(),
      Instant.now().toEpochMilli()
    );
    return effects().updateState(order).thenReply(done());
  }
}
