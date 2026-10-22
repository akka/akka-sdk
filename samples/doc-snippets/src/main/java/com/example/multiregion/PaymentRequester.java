package com.example.multiregion;

// tag::consumer[]
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "multi-region-payment-requester")
@Consume.FromEventSourcedEntity(OrderEntity.class)
public class PaymentRequester extends Consumer {

  private final PaymentProvider payments;

  public PaymentRequester(PaymentProvider payments) {
    this.payments = payments;
  }

  public Effect onOrderPlaced(OrderPlaced event) {
    return effects()
      .asyncDone(
        payments.charge(event.paymentOperationId(), event.orderId(), event.amountInCents())
      ); // <1>
  }
}
// end::consumer[]
