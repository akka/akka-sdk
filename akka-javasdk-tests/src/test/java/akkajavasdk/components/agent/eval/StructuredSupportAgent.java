/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.eval;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akkajavasdk.components.agent.eval.AccountSupportAgent.Question;
import java.util.List;

/**
 * The same support assistant as {@link AccountSupportAgent}, with a command handler that replies
 * with its own type.
 *
 * <p>The reply names who asked next to the answer, so a case for this agent carries an {@link
 * Answer} as its expected reply.
 */
@Component(id = "structured-support-agent")
public class StructuredSupportAgent extends Agent {

  /**
   * The structured reply of the support assistant.
   *
   * @param customerId who asked
   * @param text the answer
   */
  public record Answer(String customerId, String text) {}

  private static final String SYSTEM_MESSAGE =
      """
      You are a customer support assistant.

      Look the customer up with getCustomer before saying anything about them, and read their
      open tickets with openTickets when the question is about what they are waiting on.
      Never guess a name, a tier or a ticket. Answer in one or two sentences.
      """;

  private final CrmClient crm;

  public StructuredSupportAgent(CrmClient crm) {
    this.crm = crm;
  }

  public Effect<Answer> answer(Question question) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage("Customer " + question.customerId() + " asks: " + question.text())
        .map(text -> new Answer(question.customerId(), text))
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
