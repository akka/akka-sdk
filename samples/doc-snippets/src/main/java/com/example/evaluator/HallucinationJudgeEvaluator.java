package com.example.evaluator;

// tag::all[]
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;
import java.util.stream.Collectors;

@Component(id = "hallucination-judge-evaluator")
public class HallucinationJudgeEvaluator extends Evaluator {

  // tag::prompt[]
  private static final String SYSTEM_MESSAGE =
    """
    In this task, you will be presented with a [Query], a [Reference text] and an
    [Answer]. The answer is generated to the question based on the reference text.
    The answer may contain false information. You must use the reference text to
    determine if the answer to the question contains false information, if the
    answer is a hallucination of facts. Your objective is to determine whether the
    answer text contains factual information and is not a hallucination. A
    'hallucination' refers to an answer that is not based on the reference text or
    assumes information that is not available in the reference text.

    Is the answer factual or hallucinated based on the query and reference text?

    Please read the query, reference text and answer carefully, then write out in a
    step by step manner an EXPLANATION to show how to determine if the answer is
    "factual" or "hallucinated". Avoid simply stating the correct answer at the
    outset. Your response LABEL should be a single word: either "factual" or
    "hallucinated", and it should not include any other text or characters.
    "hallucinated" indicates that the answer provides factually inaccurate
    information to the query based on the reference text. "factual" indicates that
    the answer to the question is correct relative to the reference text, and does
    not contain made up information.

    Your response must be a single JSON object with the following fields:
    - "explanation": An explanation of your reasoning for why the label is "factual" or "hallucinated"
    - "label": A string, either "factual" or "hallucinated".
    """;

  private static final String USER_MESSAGE =
    """
    [Query]
    ************
    %s
    ************
    [Reference text]
    ************
    %s
    ************
    [Answer]
    ************
    %s
    ************
    """;
  // end::prompt[]
  private final LedgerClient ledger;
  private final ComponentClient componentClient;

  public HallucinationJudgeEvaluator(LedgerClient ledger, ComponentClient componentClient) {
    this.ledger = ledger;
    this.componentClient = componentClient;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    InteractionRecord interaction = ledger.getInteraction(context.subject().interactionId());
    String answer = interaction.finalResponseText();
    if (interaction.failed() || answer.isBlank()) {
      return effects().inconclusive("no answer to evaluate");
    }

    String query = interaction.inputText();
    String referenceText = interaction
      .toolCalls()
      .stream()
      .filter(call -> !call.response().isBlank()) // <1>
      .map(call -> call.name() + ": " + call.response())
      .collect(Collectors.joining("\n")); // <2>
    if (referenceText.isEmpty()) {
      return effects().inconclusive("no tool results to check the answer against"); // <3>
    }

    LabelJudge.Verdict verdict = componentClient
      .forAgent()
      .inSession(context.evaluationId() + "-label-judge")
      .method(LabelJudge::judge)
      .invoke(
        new LabelJudge.Request(
          SYSTEM_MESSAGE,
          USER_MESSAGE.formatted(query, referenceText, answer)
        )
      );

    return effects().complete(verdict.toEvaluation("factual", "hallucinated"));
  }
}
// end::all[]
