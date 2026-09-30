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
public class TranscriptJudgeDurableEvaluator
  extends DurableEvaluator<TranscriptJudgeDurableEvaluator.State> { // <1>

  public record State(String transcript) {} // <2>

  private final LedgerClient ledger;
  private final ComponentClient componentClient;

  public TranscriptJudgeDurableEvaluator(LedgerClient ledger, ComponentClient componentClient) {
    this.ledger = ledger;
    this.componentClient = componentClient;
  }

  @Override
  public Settings settings() { // <3>
    return Settings.defaults()
      .withEvaluationTimeout(Duration.ofMinutes(5))
      .withDefaultStepTimeout(Duration.ofSeconds(30))
      .withMaxStepRetries(2);
  }

  @Override
  public Effect onEvaluation(EvaluationContext context) { // <4>
    return effects().transitionTo(TranscriptJudgeDurableEvaluator::fetchTranscript);
  }

  private Effect fetchTranscript() { // <5>
    InteractionRecord interaction = ledger.getInteraction(
      evaluationContext().subject().interactionId()
    );
    if (interaction.failed()) {
      return effects().inconclusive("interaction failed, nothing to evaluate");
    }
    return effects()
      .updateState(new State(interaction.transcript())) // <6>
      .transitionTo(TranscriptJudgeDurableEvaluator::judge);
  }

  private Effect judge() {
    QualityJudge.Verdict verdict = componentClient
      .forAgent()
      .inSession(evaluationContext().evaluationId() + "-quality-judge")
      .method(QualityJudge::evaluate)
      .invoke(currentState().transcript()); // <7>

    return effects()
      .complete(Evaluation.of(verdict.passed(), verdict.reason()).withScore(verdict.score())); // <8>
  }
}
// end::all[]
