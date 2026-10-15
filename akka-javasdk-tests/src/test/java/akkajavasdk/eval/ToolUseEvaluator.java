/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.eval;

import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;
import akka.javasdk.ledger.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Checks that the support agent used its CRM tools as its instructions say: a question that names a
 * customer looks that customer up, a question about what the customer is waiting on also reads
 * their tickets, and a question without a customer id calls no tool.
 */
@Component(id = "tool-use")
public class ToolUseEvaluator extends Evaluator {

  static final Pattern CUSTOMER_ID = Pattern.compile("cust_\\d+");
  static final Pattern TICKET_QUESTION =
      Pattern.compile("(?i)\\btickets?\\b|waiting on|\\bopen\\b");

  private final LedgerClient ledger;

  public ToolUseEvaluator(LedgerClient ledger) {
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    var interaction = ledger.getInteraction(context.subject().interactionId());
    var calls = interaction.toolCalls();
    var customerId = customerId(interaction);
    if (customerId.isEmpty()) {
      return calls.isEmpty()
          ? effects().complete(Evaluation.passed("no tool for a question without a customer"))
          : effects()
              .complete(
                  Evaluation.failed(
                      "called " + names(calls) + " for a question that names no customer"));
    }
    var lookups = callsTo(calls, "getCustomer");
    if (lookups.size() != 1) {
      return effects()
          .complete(Evaluation.failed("expected one getCustomer call, called " + names(calls)));
    }
    var lookedUp = argument(lookups.getFirst(), "customerId");
    if (!customerId.get().equals(lookedUp)) {
      return effects()
          .complete(
              Evaluation.failed(
                  "looked up " + lookedUp + ", the question names " + customerId.get()));
    }
    var tickets = callsTo(calls, "openTickets");
    if (TICKET_QUESTION.matcher(interaction.inputText()).find()) {
      if (tickets.size() != 1
          || calls.indexOf(tickets.getFirst()) < calls.indexOf(lookups.getFirst())) {
        return effects()
            .complete(
                Evaluation.failed(
                    "expected openTickets once, after getCustomer; called " + names(calls)));
      }
      if (!customerId.get().equals(argument(tickets.getFirst(), "customerId"))) {
        return effects()
            .complete(Evaluation.failed("read the tickets of another customer than asked"));
      }
      return effects().complete(Evaluation.passed("looked the customer up and read their tickets"));
    }
    if (!tickets.isEmpty()) {
      return effects()
          .complete(Evaluation.failed("read tickets for a question that does not ask for them"));
    }
    return effects().complete(Evaluation.passed("looked the customer up"));
  }

  static Optional<String> customerId(InteractionRecord interaction) {
    var match = CUSTOMER_ID.matcher(interaction.inputText());
    return match.find() ? Optional.of(match.group()) : Optional.empty();
  }

  // A tool method is registered as <Agent>_<method>.
  static List<ToolCall> callsTo(List<ToolCall> calls, String method) {
    return calls.stream().filter(call -> call.name().endsWith("_" + method)).toList();
  }

  static String argument(ToolCall call, String name) {
    var arguments = JsonSupport.getObjectMapper().convertValue(parse(call.arguments()), Map.class);
    return String.valueOf(arguments.get(name));
  }

  private static Object parse(String json) {
    try {
      return JsonSupport.getObjectMapper().readTree(json);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("tool arguments are not JSON", e);
    }
  }

  private static List<String> names(List<ToolCall> calls) {
    return calls.stream().map(ToolCall::name).toList();
  }
}
