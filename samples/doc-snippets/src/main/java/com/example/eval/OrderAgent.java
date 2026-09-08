package com.example.eval;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

// tag::class[]
@Component(id = "order-agent")
public class OrderAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
    You are the support assistant of an online shop.
    Look an order up with getOrder before saying anything about it.
    Issue a refund with issueRefund only when the customer asks for one and the order
    has been delivered. Refund the full order total unless the customer names a smaller
    amount. Never invent an order, a status or an amount. Answer in one or two sentences.
    """;

  private final OrderService orders;

  public OrderAgent(OrderService orders) { // <1>
    this.orders = orders;
  }

  public Effect<String> ask(String question) { // <2>
    return effects().systemMessage(SYSTEM_MESSAGE).userMessage(question).thenReply();
  }

  @FunctionTool(name = "getOrder", description = "Look up an order by its id, for example o_42.")
  Order getOrder(String orderId) { // <3>
    return orders.getOrder(orderId);
  }

  @FunctionTool(
    name = "issueRefund",
    description = "Refund an amount in cents to the customer of an order."
  )
  Refund issueRefund(String orderId, int amountCents) {
    return orders.issueRefund(orderId, amountCents);
  }
}
// end::class[]
