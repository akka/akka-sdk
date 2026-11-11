package com.example.eval;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.evaluation.Subject;
import akka.javasdk.ledger.LedgerClient;

// tag::all[]
/**
 * Judges the reply of an interaction with {@link OrderJudge}. The configuration of the evaluator
 * gives the criterion and the score at which the reply passes.
 */
@Component(id = "judge")
public class JudgeEvaluator extends Evaluator {

  private final ComponentClient componentClient;
  private final LedgerClient ledger;

  public JudgeEvaluator(ComponentClient componentClient, LedgerClient ledger) {
    this.componentClient = componentClient;
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    var criterion = context.config().getString("criterion"); // <1>
    var threshold = context.config().getDouble("threshold");

    var subject = (Subject.Interaction) context.subject();
    var interaction = ledger.getInteraction(subject.interactionId());
    if (interaction.failed()) {
      return effects().inconclusive("the interaction failed, there is no reply to judge");
    }

    var verdict = componentClient
      .forAgent()
      .inSession(context.evaluationId()) // <2>
      .method(OrderJudge::judge)
      .invoke(new OrderJudge.Request(criterion, interaction.transcript())); // <3>

    return effects()
      .complete(
        Evaluation.of(verdict.score() >= threshold, verdict.reason()).withScore(
          verdict.score()
        ) // <4>
      );
  }
}
// end::all[]
