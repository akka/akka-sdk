package com.example.eval;

// tag::class[]
/**
 * The shop's order system as the agent sees it. Production wires a client for the real system,
 * an evaluation wires a stub that answers with what a case primed.
 */
public interface OrderService {
  /** The order, or a thrown {@link java.util.NoSuchElementException} when the id is unknown. */
  Order getOrder(String orderId);

  Refund issueRefund(String orderId, int amountCents);
}
// end::class[]
