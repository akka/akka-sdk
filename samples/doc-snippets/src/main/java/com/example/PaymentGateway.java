package com.example;

/** Charges the payment of an order and returns the payment id. */
public interface PaymentGateway {
  String charge(String orderId);
}
