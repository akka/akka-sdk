package com.example.multiregion;

// tag::event[]
public record OrderPlaced(String orderId, String paymentOperationId, long amountInCents)
  implements OrderEvent {}
// end::event[]
