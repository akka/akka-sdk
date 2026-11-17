package com.example.eval;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

// tag::all[]
/** Scores the reply of one interaction against a criterion. Called by {@link JudgeEvaluator}. */
@Component(id = "order-judge")
public class OrderJudge extends Agent {

  /**
   * @param criterion what the reply is judged against
   * @param transcript the interaction as the ledger recorded it
   */
  public record Request(String criterion, String transcript) {}

  /**
   * @param score how well the reply meets the criterion, from 0.0 to 1.0
   * @param reason one sentence explaining the score
   */
  public record Verdict(double score, String reason) {} // <1>

  private static final String SYSTEM_MESSAGE =
    """
    You judge the replies of a support assistant of an online shop.

    You get a criterion and the transcript of one interaction: the instructions of the
    assistant, the message of the customer, the tool calls with their results, and the reply.
    Score how well the final reply meets the criterion, from 0.0 to 1.0.

    Answer with a JSON object with the fields 'score' (a number from 0.0 to 1.0) and 'reason'
    (one sentence).
    """;

  public Effect<Verdict> judge(Request request) {
    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(
        "Criterion: " + request.criterion() + "\n\nTranscript:\n" + request.transcript()
      )
      .responseConformsTo(Verdict.class) // <2>
      .thenReply();
  }
}
// end::all[]
