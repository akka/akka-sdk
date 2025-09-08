package demo.multiagent.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;

import java.util.Locale;

@ComponentId("summarization-evaluator-agent")
public class SummarizationEvaluatorAgent extends Agent {

  public record EvaluationRequest(
      String source,
      String summary
  ) {}

  public record EvaluationResult(String evaluation, String justification) {
    public boolean ok() {
      return switch (evaluation.toLowerCase(Locale.ROOT)) {
        case "good" -> true;
        case "bad" -> false;
        default -> throw new IllegalArgumentException("Unknown evaluation result [" + evaluation + "]");
      };
    }
  }

  private static final String SYSTEM_MESSAGE =
    """
    You are an expert editor. Your objective is to provide a single, holistic judgment on
    whether the [Summary] is "Good" or "Bad" based on the [Source Document].
    
    A summary is "Bad" if it meets ANY of the following failure conditions:
    - **It is NOT Faithful:** It contains factual errors, or any information not present in the source.
    - **It is NOT Comprehensive:** It misses the central message or the most critical points of the source.
    - **It is NOT Coherent:** It is unclear, illogical, or grammatically incorrect.
    
    A summary is **"Good"** only if it is faithful, comprehensive, and coherent.
    
    Response as a single JSON object with the required fields.
    - "evaluation": A string, either "Good" or "Bad".
    - "justification": A brief explanation for your decision.
    """.stripIndent();

  private static final String USER_MESSAGE =
    """
    [Source Document]
    %s

    [Summary]
    %s
    """.stripIndent();


  public Effect<EvaluationResult> evaluate(EvaluationRequest request) {
    String evaluationPrompt = USER_MESSAGE.formatted(request.source, request.summary);

    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(evaluationPrompt)
      .responseConformsTo(EvaluationResult.class)
      .thenReply();
  }

}
