package com.example.multiregion;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

class OrderEntityTest {

  @Test
  void retryDoesNotCreateAnotherPaymentOperation() {
    var entity = EventSourcedTestKit.of("order-123", OrderEntity::new);
    var placed = entity.method(OrderEntity::place).invoke(2500L);
    assertThat(placed.getAllEvents()).containsExactly(
      new OrderPlaced("order-123", "order-payment/order-123", 2500)
    );

    var repeated = entity.method(OrderEntity::place).invoke(2500L);
    assertThat(repeated.isError()).isFalse();
    assertThat(repeated.getAllEvents()).isEmpty();

    var changed = entity.method(OrderEntity::place).invoke(5000L);
    assertThat(changed.isError()).isTrue();
    assertThat(changed.getAllEvents()).isEmpty();
    assertThat(entity.getState().amountInCents()).isEqualTo(2500);
  }

  @Test
  void invalidAmountDoesNotPersistAnOrder() {
    var entity = EventSourcedTestKit.of("order-123", OrderEntity::new);
    var result = entity.method(OrderEntity::place).invoke(0L);
    assertThat(result.isError()).isTrue();
    assertThat(result.getAllEvents()).isEmpty();
  }
}
