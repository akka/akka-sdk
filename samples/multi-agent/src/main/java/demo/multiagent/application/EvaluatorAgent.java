package demo.multiagent.application;

// tag::all[]
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component(id = "evaluator-agent")
@AgentDescription(
  name = "Evaluator Agent",
  description = """
  An agent that acts as an LLM judge to evaluate the quality of AI responses.
  It assesses whether the final answer is appropriate for the original question
  and checks for any deviations from user preferences.
  """
)
public class EvaluatorAgent extends Agent {

  public record EvaluationRequest(
    String userId,
    String originalRequest,
    String finalAnswer
  ) {}

  public record EvaluationResult(String evaluation, String feedback) {
    public boolean ok() {
      return switch (evaluation.toLowerCase(Locale.ROOT)) {
        case "correct" -> true;
        case "incorrect" -> false;
        default -> throw new IllegalArgumentException(
          "Unknown evaluation result [" + evaluation + "]"
        );
      };
    }
  }

  private static final String SYSTEM_MESSAGE = // <1>
    """
    You are an evaluator agent that acts as an LLM judge. Your job is to evaluate
    the quality and appropriateness of AI-generated responses.

    Your evaluation should focus on:
    1. Whether the final answer appropriately addresses the original question
    2. Whether the answer respects and aligns with the user's stated preferences
    3. The overall quality, relevance, and helpfulness of the response
    4. Any potential deviations or inconsistencies with user preferences

    A response is "Incorrect" if it meets ANY of the following failure conditions:
    - poor response with significant issues or minor preference violations
    - unacceptable response that fails to address the question or violates preferences

    A response is "Correct" if it:
    - fully addresses the question and respects all preferences
    - good response with minor issues but respects preferences

    IMPORTANT:
    - Any violations of user preferences should result in an incorrect evaluation since
      respecting user preferences is the most important criteria

    Your response must be a single JSON object with the following fields:
    - "evaluation": A string, either "Correct" or "Incorrect".
    - "feedback": Specific feedback on what works well or deviations from preferences.
    """.stripIndent();

  private final ComponentClient componentClient;

  public EvaluatorAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<EvaluationResult> evaluate(EvaluationRequest request) {
    var allPreferences = componentClient
      .forEventSourcedEntity(request.userId())
      .method(PreferencesEntity::getPreferences)
      .invoke(); // <2>

    String evaluationPrompt = buildEvaluationPrompt(
      request.originalRequest(),
      request.finalAnswer(),
      allPreferences.entries()
    );

    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(evaluationPrompt)
      .responseConformsTo(EvaluationResult.class) // <3>
      .thenReply();
  }

  private String buildEvaluationPrompt(
    String originalRequest,
    String finalAnswer,
    List<String> preferences
  ) {
    StringBuilder prompt = new StringBuilder();

    prompt.append("ORIGINAL REQUEST:\n").append(originalRequest).append("\n\n");

    prompt.append("FINAL ANSWER TO EVALUATE:\n").append(finalAnswer).append("\n\n");

    if (!preferences.isEmpty()) {
      prompt
        .append("USER PREFERENCES:\n")
        .append(preferences.stream().collect(Collectors.joining("\n", "- ", "")))
        .append("\n\n");
    }

    prompt
      .append("Please evaluate the final answer against the original request")
      .append(preferences.isEmpty() ? "." : " and user preferences.");

    return prompt.toString();
  }
}
// end::all[]
