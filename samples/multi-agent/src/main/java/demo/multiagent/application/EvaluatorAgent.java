package demo.multiagent.application;

// tag::all[]

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.annotations.Component;
import java.util.List;

@Component(
  id = "evaluator-agent",
  name = "Evaluator Agent",
  description = """
  An agent that acts as a judge of the quality of AI responses. \
  It assesses whether the final answer appropriately addresses the original request \
  and respects the user's preferences.\
  """
)
public class EvaluatorAgent extends Agent {

  public record EvaluationRequest(
    String originalRequest,
    List<String> preferences,
    String finalAnswer
  ) {}

  public record Result(String explanation, String label) {}

  private static final String SYSTEM_MESSAGE =
    """
    You are an evaluator agent that acts as a judge. Your job is to evaluate
    the quality and appropriateness of AI-generated responses.

    The request comes with the user's preferences. Your evaluation should focus on:
    1. Whether the final answer appropriately addresses the original question
    2. Whether the answer respects and aligns with the user's preferences
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
    var preferences = request.preferences().isEmpty()
      ? "(none)"
      : String.join("\n", request.preferences().stream().map(p -> "- " + p).toList());
    var prompt =
      """
      ORIGINAL REQUEST:
      %s

      USER PREFERENCES:
      %s

      FINAL ANSWER TO EVALUATE:
      %s

      Please evaluate the final answer against the original request and the preferences.
      """.formatted(request.originalRequest(), preferences, request.finalAnswer());

    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .memory(MemoryProvider.none())
      .userMessage(prompt)
      .responseConformsTo(Result.class)
      .thenReply();
  }
}
// end::all[]
