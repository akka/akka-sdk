package com.example.eval;

// tag::class[]
/**
 * The order system the agent's tools call. Production provides a client for the real system, a
 * test provides a stub.
 */
public interface OrderService {
  /** The order, or a thrown {@link java.util.NoSuchElementException} when the id is unknown. */
  Order getOrder(String orderId);

  Refund issueRefund(String orderId, int amountCents);
}
// end::class[]
