package com.example.evaluator;

// tag::all[]
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.DurableEvaluator;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;
import java.time.Duration;

@Component(id = "transcript-judge-durable-evaluator")
public class TranscriptJudgeDurableEvaluator extends DurableEvaluator<Void> { // <1>

  private final LedgerClient ledger;
  private final ComponentClient componentClient;

  public TranscriptJudgeDurableEvaluator(
    LedgerClient ledger,
    ComponentClient componentClient
  ) {
    this.ledger = ledger;
    this.componentClient = componentClient;
  }

  @Override
  public Settings settings() { // <2>
    return Settings.defaults()
      .withEvaluationTimeout(Duration.ofMinutes(5))
      .withDefaultStepTimeout(Duration.ofSeconds(30))
      .withMaxStepRetries(2);
  }

  @Override
  public Effect onEvaluation(EvaluationContext context) { // <3>
    return effects().transitionTo(TranscriptJudgeDurableEvaluator::judge);
  }

  private Effect judge() { // <4>
    InteractionRecord interaction = ledger.getInteraction(
      evaluationContext().subject().interactionId()
    );
    if (interaction.failed()) {
      return effects().inconclusive("interaction failed, nothing to evaluate");
    }

    QualityJudge.Verdict verdict = componentClient
      .forAgent()
      .inSession(evaluationContext().evaluationId() + "-quality-judge") // <5>
      .method(QualityJudge::evaluate)
      .invoke(interaction.transcript());

    return effects()
      .complete(Evaluation.of(verdict.passed(), verdict.reason()).withScore(verdict.score())); // <6>
  }
}
// end::all[]
