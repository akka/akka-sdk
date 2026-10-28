package com.example.evaluator;

// tag::all[]
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;

@Component(id = "summarization-judge-evaluator")
public class SummarizationJudgeEvaluator extends Evaluator {

  // tag::prompt[]
  static final String SYSTEM_MESSAGE =
    """
    You are comparing the summary text and its original document and trying to
    determine if the summary is good.

    Compare the [Summary] to the [Original Document]. First, write out in a step by
    step manner an EXPLANATION to show how to determine if the Summary is
    comprehensive, concise, coherent, and independent relative to the Original
    Document. Avoid simply stating the correct answer at the outset. Your response
    LABEL must be a single word, either "good" or "bad", and should not contain any
    text or characters aside from that. "bad" means that the Summary is not
    comprehensive, concise, coherent, and independent relative to the Original
    Document. "good" means the Summary is comprehensive, concise, coherent, and
    independent relative to the Original Document.

    Your response must be a single JSON object with the following fields:
    - "explanation": An explanation of your reasoning for why the label is "good" or "bad"
    - "label": A string, either "good" or "bad".
    """;

  static final String USER_MESSAGE =
    """
    [Summary]
    ************
    %s
    ************
    [Original Document]
    ************
    %s
    ************
    """;
  // end::prompt[]
  private final LedgerClient ledger;
  private final ComponentClient componentClient;

  public SummarizationJudgeEvaluator(LedgerClient ledger, ComponentClient componentClient) {
    this.ledger = ledger;
    this.componentClient = componentClient;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    InteractionRecord interaction = ledger.getInteraction(context.subject().interactionId());
    String document = interaction.inputText(); // <1>
    String summary = interaction.finalResponseText(); // <2>
    if (interaction.failed() || document.isBlank() || summary.isBlank()) {
      return effects().inconclusive("no document or no summary to evaluate");
    }

    LabelJudge.Verdict verdict = componentClient
      .forAgent()
      .inSession(context.evaluationId() + "-label-judge")
      .method(LabelJudge::judge)
      .invoke(
        new LabelJudge.Request(SYSTEM_MESSAGE, USER_MESSAGE.formatted(summary, document))
      );

    return effects().complete(verdict.toEvaluation("good", "bad"));
  }
}
// end::all[]
