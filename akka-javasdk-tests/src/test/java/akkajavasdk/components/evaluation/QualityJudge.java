/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

/** A judge agent (LLM-as-judge) used by the agent-as-judge evaluator integration test. */
@Component(
    id = "quality-judge",
    name = "Quality Judge",
    description = "Judges the quality of a conversation and returns a structured verdict.")
public class QualityJudge extends Agent {

  public record Verdict(boolean passed, double score, String reason) {}

  public Effect<Verdict> evaluate(String transcript) {
    return effects()
        .systemMessage(
            "You are a conversation quality judge. Respond with a JSON object with fields"
                + " 'passed' (boolean), 'score' (number) and 'reason' (string).")
        .userMessage("Evaluate this conversation:\n" + transcript)
        .responseConformsTo(Verdict.class)
        .thenReply();
  }
}
