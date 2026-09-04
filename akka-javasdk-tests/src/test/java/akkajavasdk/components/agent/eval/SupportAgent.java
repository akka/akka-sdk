/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.eval;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import java.util.List;

/**
 * The sample service under evaluation: a support assistant with two CRM-backed tools.
 *
 * <p>The tools are declared on the agent and delegate to the injected {@link CrmClient}, so the
 * names the model sees ({@code SupportAgent_getCustomer}) do not change with whatever
 * implementation of the client is wired in. Swapping the client is how a test controls the world
 * without touching the agent.
 */
@Component(id = "support-agent")
public class SupportAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
      """
      You are a customer support assistant.

      Look a customer up with getCustomer before saying anything about them, and read their
      open tickets with openTickets when the question is about what they are waiting on.
      Never guess a name, a tier or a ticket. Answer in one or two sentences.
      """;

  private final CrmClient crm;

  public SupportAgent(CrmClient crm) {
    this.crm = crm;
  }

  public Effect<String> ask(String question) {
    return effects().systemMessage(SYSTEM_MESSAGE).userMessage(question).thenReply();
  }

  @FunctionTool(description = "Look up a customer record by its id, for example cust_1.")
  Customer getCustomer(String customerId) {
    return crm.getCustomer(customerId);
  }

  @FunctionTool(description = "List the open support tickets of a customer, by customer id.")
  List<Ticket> openTickets(String customerId) {
    return crm.openTickets(customerId);
  }
}
