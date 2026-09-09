package com.example.eval;

import akka.javasdk.testkit.eval.RecordedCall;
import akka.javasdk.testkit.eval.ToolBindings;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Answers with the orders a case added. A curated case adds them in Java, a replayed case through
 * {@link ToolBindings}. */
public final class StubOrderService implements OrderService {

  private final Map<String, Order> orders = new ConcurrentHashMap<>();
  private final Map<String, Refund> refunds = new ConcurrentHashMap<>();
  private final List<Refund> issued = new CopyOnWriteArrayList<>();

  public void reset() {
    orders.clear();
    refunds.clear();
    issued.clear();
  }

  public void addOrder(Order order) {
    orders.put(order.id(), order);
  }

  public void addRefund(Refund refund) {
    refunds.put(refund.orderId(), refund);
  }

  /** The refunds the agent issued since the last reset. */
  public List<Refund> issued() {
    return List.copyOf(issued);
  }

  @Override
  public Order getOrder(String orderId) {
    var order = orders.get(orderId);
    if (order == null) throw new NoSuchElementException("no order " + orderId);
    return order;
  }

  @Override
  public Refund issueRefund(String orderId, int amountCents) {
    var refund = refunds.getOrDefault(
      orderId,
      new Refund(orderId, amountCents, "rf_" + (issued.size() + 1))
    );
    issued.add(refund);
    return refund;
  }

  // tag::loaders[]
  /** Adds the order a recorded getOrder call returned. */
  public void loadOrder(RecordedCall call) {
    addOrder(call.resultAs(Order.class));
  }

  /** Adds the refund a recorded issueRefund call returned. */
  public void loadRefund(RecordedCall call) {
    addRefund(call.resultAs(Refund.class));
  }
  // end::loaders[]
}
