/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.eval;

import akka.javasdk.testkit.eval.RecordedCall;
import akka.javasdk.testkit.eval.ToolBindings;
import akkajavasdk.components.agent.eval.CrmClient;
import akkajavasdk.components.agent.eval.Customer;
import akkajavasdk.components.agent.eval.Ticket;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mocked dependency: canned answers, nothing written down. The test fills it with plain Java
 * before the run; a replayed case fills it through {@link ToolBindings} with what production
 * recorded.
 */
final class CannedCrmClient implements CrmClient {

  private final Map<String, Customer> customers = new ConcurrentHashMap<>();
  private final Map<String, List<Ticket>> tickets = new ConcurrentHashMap<>();

  @Override
  public Customer getCustomer(String customerId) {
    var customer = customers.get(customerId);
    if (customer == null) throw new NoSuchElementException("no customer " + customerId);
    return customer;
  }

  @Override
  public List<Ticket> openTickets(String customerId) {
    return tickets.getOrDefault(customerId, List.of());
  }

  void reset() {
    customers.clear();
    tickets.clear();
  }

  void add(Customer... canned) {
    for (var customer : canned) customers.put(customer.id(), customer);
  }

  void addTickets(String customerId, Ticket... canned) {
    tickets.put(customerId, List.of(canned));
  }

  /** {@link ToolBindings.ResultLoader} for getCustomer. */
  void loadCustomer(RecordedCall call) {
    customers.put((String) call.argument("customerId"), call.resultAs(Customer.class));
  }

  /** {@link ToolBindings.ResultLoader} for openTickets; the recorded result is an array of them. */
  void loadTickets(RecordedCall call) {
    tickets.put((String) call.argument("customerId"), List.of(call.resultAs(Ticket[].class)));
  }
}
