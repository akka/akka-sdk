package com.example.evaluator;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.evaluation.Subject;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;
import java.util.stream.Stream;

// tag::all[]
@Component(id = "agent-team-run-evaluator") // <1>
public class AgentTeamRunEvaluator extends Evaluator {

  private final LedgerClient ledger;

  public AgentTeamRunEvaluator(LedgerClient ledger) {
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    if (!(context.subject() instanceof Subject.Workflow run)) { // <2>
      return effects().notApplicable("only runs of the agent team are evaluated");
    }
    var interactionIds = context.attributes().get("interactionIds").split(","); // <3>
    var interactions = Stream.of(interactionIds).map(ledger::getInteraction).toList(); // <4>
    if (interactions.stream().anyMatch(InteractionRecord::failed)) {
      return effects().inconclusive("an agent call of run " + run.workflowId() + " failed");
    }

    var answer = interactions.getLast().finalResponseText(); // <5>
    return effects()
      .complete(
        Evaluation.of(
          !answer.isBlank(),
          "run of " +
          interactions.size() +
          " agent calls, answer of " +
          answer.length() +
          " characters"
        ).withAttribute("agentCalls", Integer.toString(interactions.size())) // <6>
      );
  }
}
// end::all[]
