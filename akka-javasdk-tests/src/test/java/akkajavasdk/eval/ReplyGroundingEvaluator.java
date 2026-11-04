/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.eval;

import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.evaluation.Subject;
import akka.javasdk.ledger.LedgerClient;
import akkajavasdk.components.agent.eval.Customer;
import akkajavasdk.components.agent.eval.Ticket;
import java.util.Arrays;
import java.util.List;

/**
 * Checks that the support agent's reply is grounded in what the CRM returned: the customer's name
 * when a customer was looked up, the subject of every open ticket when the tickets were read, and a
 * request for a customer id when the question named none.
 */
@Component(id = "reply-grounding")
public class ReplyGroundingEvaluator extends Evaluator {

  private final LedgerClient ledger;

  public ReplyGroundingEvaluator(LedgerClient ledger) {
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    var subject = (Subject.Interaction) context.subject();
    var interaction = ledger.getInteraction(subject.interactionId());
    if (interaction.failed()) {
      return effects().inconclusive("the interaction failed, there is no reply to check");
    }
    var reply = interaction.finalResponseText();
    if (ToolUseEvaluator.customerId(interaction).isEmpty()) {
      return reply.contains("customer id")
          ? effects().complete(Evaluation.passed("asks for a customer id"))
          : effects().complete(Evaluation.failed("does not ask for a customer id"));
    }
    var lookups = ToolUseEvaluator.callsTo(interaction.toolCalls(), "getCustomer");
    if (lookups.isEmpty()) {
      return effects().notApplicable("no customer was looked up, nothing to ground the reply in");
    }
    var customer = JsonSupport.decodeJson(Customer.class, lookups.getFirst().response().getBytes());
    if (!reply.contains(customer.name())) {
      return effects().complete(Evaluation.failed("does not name " + customer.name()));
    }
    var tickets = ToolUseEvaluator.callsTo(interaction.toolCalls(), "openTickets");
    if (!tickets.isEmpty()) {
      List<Ticket> open =
          Arrays.asList(
              JsonSupport.decodeJson(Ticket[].class, tickets.getFirst().response().getBytes()));
      var missing = open.stream().map(Ticket::subject).filter(s -> !reply.contains(s)).toList();
      if (!missing.isEmpty()) {
        return effects().complete(Evaluation.failed("leaves out the tickets " + missing));
      }
    }
    return effects().complete(Evaluation.passed("names the customer and their tickets"));
  }
}
