package com.example.multiregion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.Done;
import akka.javasdk.testkit.ConsumerTestKit;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OrderConsumersTest {

  private final OrderPlaced order = new OrderPlaced(
    "order-123",
    "order-payment/order-123",
    2500
  );

  @Test
  void duplicateDeliveryCreatesOneCharge() {
    var provider = new FakePaymentProvider();
    var consumer = ConsumerTestKit.of(() -> new PaymentRequester(provider));

    assertThat(
      consumer.method(PaymentRequester::onOrderPlaced).invoke(order).isConsumed()
    ).isTrue();
    assertThat(
      consumer.method(PaymentRequester::onOrderPlaced).invoke(order).isConsumed()
    ).isTrue();

    assertThat(provider.attempts.get()).isEqualTo(2);
    assertThat(provider.charges).containsOnlyKeys(order.paymentOperationId());
  }

  @Test
  void independentConsumersUseTheSameKeyForConcurrentAttempts() {
    var provider = new FakePaymentProvider();
    var first = ConsumerTestKit.of(() -> new PaymentRequester(provider));
    var second = ConsumerTestKit.of(() -> new PaymentRequester(provider));
    var start = new CompletableFuture<Void>();

    var firstAttempt = start.thenRunAsync(
      () ->
        assertThat(
          first.method(PaymentRequester::onOrderPlaced).invoke(order).isConsumed()
        ).isTrue()
    );
    var secondAttempt = start.thenRunAsync(
      () ->
        assertThat(
          second.method(PaymentRequester::onOrderPlaced).invoke(order).isConsumed()
        ).isTrue()
    );
    start.complete(null);
    CompletableFuture.allOf(firstAttempt, secondAttempt).join();

    assertThat(provider.attempts.get()).isEqualTo(2);
    assertThat(provider.charges).containsOnlyKeys(order.paymentOperationId());
  }

  @Test
  void lostResponseCanBeRetriedByAnotherConsumer() {
    var provider = new FakePaymentProvider();
    provider.loseNextResponse.set(true);
    var first = ConsumerTestKit.of(() -> new PaymentRequester(provider));

    assertThatThrownBy(
      () -> first.method(PaymentRequester::onOrderPlaced).invoke(order)
    ).hasStackTraceContaining("Payment response lost");
    assertThat(provider.charges).hasSize(1);

    var retry = ConsumerTestKit.of(() -> new PaymentRequester(provider));
    assertThat(
      retry.method(PaymentRequester::onOrderPlaced).invoke(order).isConsumed()
    ).isTrue();
    assertThat(provider.attempts.get()).isEqualTo(2);
    assertThat(provider.charges).hasSize(1);
  }

  @Test
  void operationKeyCannotBeReusedForDifferentPaymentDetails() {
    var provider = new FakePaymentProvider();
    var consumer = ConsumerTestKit.of(() -> new PaymentRequester(provider));
    consumer.method(PaymentRequester::onOrderPlaced).invoke(order);

    var changed = new OrderPlaced(order.orderId(), order.paymentOperationId(), 5000);
    assertThatThrownBy(
      () -> consumer.method(PaymentRequester::onOrderPlaced).invoke(changed)
    ).hasStackTraceContaining("Payment details differ");
    assertThat(provider.charges).containsEntry(order.paymentOperationId(), order);
  }

  @Test
  void repeatedIndexUpdateLeavesOneEntry() {
    var entries = new ConcurrentHashMap<String, OrderPlaced>();
    RegionalIndex index = (region, event) -> {
      entries.put(region + "/" + event.orderId(), event);
      return CompletableFuture.completedFuture(Done.getInstance());
    };
    var consumer = ConsumerTestKit.of(() -> new RegionalOrderIndex(index));

    assertThat(
      consumer.method(RegionalOrderIndex::onOrderPlaced).invoke(order).isConsumed()
    ).isTrue();
    assertThat(
      consumer.method(RegionalOrderIndex::onOrderPlaced).invoke(order).isConsumed()
    ).isTrue();
    assertThat(entries.values()).containsExactly(order);
  }

  // This fake models atomic deduplication at the external destination, not Consumer-local state.
  private static final class FakePaymentProvider implements PaymentProvider {

    final Map<String, OrderPlaced> charges = new ConcurrentHashMap<>();
    final AtomicInteger attempts = new AtomicInteger();
    final AtomicBoolean loseNextResponse = new AtomicBoolean();

    @Override
    public CompletionStage<Done> charge(
      String operationId,
      String orderId,
      long amountInCents
    ) {
      attempts.incrementAndGet();
      var requested = new OrderPlaced(orderId, operationId, amountInCents);
      charges.compute(operationId, (key, existing) -> {
        if (existing != null && !existing.equals(requested)) {
          throw new IllegalArgumentException("Payment details differ");
        }
        return requested;
      });
      if (loseNextResponse.compareAndSet(true, false)) {
        return CompletableFuture.failedFuture(
          new IllegalStateException("Payment response lost")
        );
      }
      return CompletableFuture.completedFuture(Done.getInstance());
    }
  }
}
