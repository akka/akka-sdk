/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.eval;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import java.util.List;

/**
 * The same support assistant as {@link SupportAgent}, with a command handler that takes its own
 * type.
 *
 * <p>The caller is identified by the command, not by the question, so a case for this agent cannot
 * be written as one string. An {@link akka.javasdk.testkit.eval.EvalCase} carries the {@link
 * Question} instead.
 */
@Component(id = "account-support-agent")
public class AccountSupportAgent extends Agent {

  /**
   * @param customerId who is asking
   * @param text what they asked
   */
  public record Question(String customerId, String text) {}

  private static final String SYSTEM_MESSAGE =
      """
      You are a customer support assistant.

      Look the customer up with getCustomer before saying anything about them, and read their
      open tickets with openTickets when the question is about what they are waiting on.
      Never guess a name, a tier or a ticket. Answer in one or two sentences.
      """;

  private final CrmClient crm;

  public AccountSupportAgent(CrmClient crm) {
    this.crm = crm;
  }

  public Effect<String> ask(Question question) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage("Customer " + question.customerId() + " asks: " + question.text())
        .thenReply();
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
