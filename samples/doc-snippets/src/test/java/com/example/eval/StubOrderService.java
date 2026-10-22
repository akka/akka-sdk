package com.example.eval;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Answers with the orders the test added before the run. */
public final class StubOrderService implements OrderService {

  private final Map<String, Order> orders = new ConcurrentHashMap<>();
  private final List<Refund> issued = new CopyOnWriteArrayList<>();

  public void reset() {
    orders.clear();
    issued.clear();
  }

  public void addOrder(Order order) {
    orders.put(order.id(), order);
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
    var refund = new Refund(orderId, amountCents, "rf_" + (issued.size() + 1));
    issued.add(refund);
    return refund;
  }
}
