package demo.multiagent.application;

// tag::all[]

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.EvaluationResult;
import akka.javasdk.annotations.Component;
import java.util.Locale;

@Component(
  id = "evaluator-agent",
  name = "Evaluator Agent",
  description = """
  An agent that acts as an LLM judge to evaluate the quality of AI responses.
  It assesses whether the final answer appropriately addresses the original request
  and respects any user preferences carried in the request.
  """
)
public class EvaluatorAgent extends Agent {

  public record EvaluationRequest(String originalRequest, String finalAnswer) {}

  public record Result(String explanation, String label) implements EvaluationResult {
    public boolean passed() {
      if (label == null) throw new IllegalArgumentException(
        "Model response must include label field"
      );

      return switch (label.toLowerCase(Locale.ROOT)) {
        case "correct" -> true;
        case "incorrect" -> false;
        default -> throw new IllegalArgumentException(
          "Unknown evaluation result [" + label + "]"
        );
      };
    }
  }

  private static final String SYSTEM_MESSAGE =
    """
    You are an evaluator agent that acts as an LLM judge. Your job is to evaluate
    the quality and appropriateness of AI-generated responses.

    The original request may include user preferences. Your evaluation should focus on:
    1. Whether the final answer appropriately addresses the original question
    2. Whether the answer respects and aligns with any stated user preferences
    3. The overall quality, relevance, and helpfulness of the response

    A response is "Incorrect" if it meets ANY of the following failure conditions:
    - poor response with significant issues or minor preference violations
    - unacceptable response that fails to address the question or violates preferences

    A response is "Correct" if it:
    - fully addresses the question and respects all stated preferences
    - good response with minor issues but respects preferences

    IMPORTANT:
    - Any violations of user preferences should result in an Incorrect evaluation since
      respecting user preferences is the most important criteria

    Your response must be a single JSON object with the following fields:
    - "explanation": Specific feedback on what works well or deviations from preferences.
    - "label": A string, either "Correct" or "Incorrect".
    """.stripIndent();

  public Effect<Result> evaluate(EvaluationRequest request) {
    var prompt =
      """
      ORIGINAL REQUEST:
      %s

      FINAL ANSWER TO EVALUATE:
      %s

      Please evaluate the final answer against the original request.
      """.formatted(request.originalRequest(), request.finalAnswer());

    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(prompt)
      .responseConformsTo(Result.class)
      .thenReply();
  }
}
// end::all[]
