package store.order.api;

import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.valueentity.ValueEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import store.order.domain.Order;

import java.time.Instant;

@TypeId("order")
@Id("id")
@RequestMapping("/order/{id}")
public class OrderEntity extends ValueEntity<Order> {

  @GetMapping()
  public Effect<Order> get() {
    return effects().reply(currentState());
  }

  @PostMapping("/create")
  public Effect<String> create(@RequestBody CreateOrder createOrder) {
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
