package com.example.evaluator;

// tag::all[]
import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.evaluation.Subject;
import akka.javasdk.ledger.LedgerClient;

@Component(id = "reply-length-evaluator")
public class ReplyLengthEvaluator extends Evaluator {

  private final LedgerClient ledger;

  public ReplyLengthEvaluator(LedgerClient ledger) {
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    int minLength = context.config().getInt("min-length"); // <1>
    var subject = (Subject.Interaction) context.subject();
    var interaction = ledger.getInteraction(subject.interactionId());
    int length = interaction.finalResponseText().length();
    return effects()
      .complete(
        Evaluation.of(
          length >= minLength,
          "reply of " + length + " characters, minimum " + minLength
        )
      );
  }
}
// end::all[]
