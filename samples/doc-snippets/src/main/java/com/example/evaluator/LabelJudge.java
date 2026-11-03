package com.example.evaluator;

// tag::all[]
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import java.util.Locale;

@Component(id = "label-judge") // <1>
public class LabelJudge extends Agent {

  public record Request(String systemMessage, String userMessage) {} // <2>

  public record Verdict(String explanation, String label) { // <3>
    public Evaluation toEvaluation(String passLabel, String failLabel) { // <4>
      var normalized = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
      if (normalized.equals(passLabel)) {
        return Evaluation.passed(explanation).withLabel(normalized);
      } else if (normalized.equals(failLabel)) {
        return Evaluation.failed(explanation).withLabel(normalized);
      } else {
        throw new IllegalArgumentException("Unknown evaluation label [" + label + "]");
      }
    }
  }

  public Effect<Verdict> judge(Request request) {
    return effects()
      .systemMessage(request.systemMessage())
      .memory(MemoryProvider.none()) // <5>
      .userMessage(request.userMessage())
      .responseConformsTo(Verdict.class)
      .thenReply();
  }
}
// end::all[]
