package com.example.evaluator;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.annotations.Component;

@Component(id = "quality-judge")
public class QualityJudge extends Agent {

  public record Verdict(boolean passed, String reason, double score) {}

  public Effect<Verdict> evaluate(String transcript) {
    return effects()
      .systemMessage(
        "Judge the quality of the interaction. Reply with a passed flag, a reason, and a score between 0 and 1."
      )
      .memory(MemoryProvider.none())
      .userMessage(transcript)
      .responseConformsTo(Verdict.class)
      .thenReply();
  }
}
