package demo.multiagent.application;

// tag::all[]
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;

import java.util.stream.Collectors;

@ComponentId("evaluator-agent")
@AgentDescription(
    name = "Evaluator Agent",
    description = """
        An agent that acts as an LLM judge to evaluate the quality of AI responses.
        It assesses whether the final answer is appropriate for the original question
        and checks for any deviations from user preferences.
        """,
    role = "worker"
)
public class EvaluatorAgent extends Agent {

    public record EvaluationRequest(String userId, String originalRequest, String finalAnswer) {}

    public record EvaluationResult(
        int score,
        String feedback
    ) {}

    private static final String SYSTEM_MESSAGE = // <1>
        """
        You are an evaluator agent that acts as an LLM judge. Your job is to evaluate
        the quality and appropriateness of AI-generated responses.
        
        Your evaluation should focus on:
        1. Whether the final answer appropriately addresses the original question
        2. Whether the answer respects and aligns with the user's stated preferences
        3. The overall quality, relevance, and helpfulness of the response
        4. Any potential deviations or inconsistencies with user preferences
        
        SCORING CRITERIA:
        - Use a score from 1-5 where:
          * 5 = Excellent response that fully addresses the question and respects all preferences
          * 4 = Good response with minor issues but respects preferences
          * 3 = Acceptable response that meets basic requirements and respects preferences
          * 2 = Poor response with significant issues or minor preference violations
          * 1 = Unacceptable response that fails to address the question or violates preferences
        
        IMPORTANT:
        - A score of 3 or higher means the answer passes evaluation (acceptable)
        - A score below 3 means the answer fails evaluation (unacceptable)
        - Any violations of user preferences should result in a failing score (below 3) since 
          respecting user preferences is the most important criteria
        
        Your response should be a JSON object with the following structure:
        {
          "score": <integer from 1-5>,
          "feedback": "<specific feedback on what works well or deviations from preferences>",
        }
        
        Do not include any explanations or text outside of the JSON structure.
        """.stripIndent();

    private final ComponentClient componentClient;

    public EvaluatorAgent(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect<EvaluationResult> evaluate(EvaluationRequest request) {
        var allPreferences =
            componentClient
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
            .responseAs(EvaluationResult.class) // <3>
            .thenReply();
    }

    private String buildEvaluationPrompt(String originalRequest, String finalAnswer, java.util.List<String> preferences) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("ORIGINAL REQUEST:\n")
            .append(originalRequest)
            .append("\n\n");

        prompt.append("FINAL ANSWER TO EVALUATE:\n")
            .append(finalAnswer)
            .append("\n\n");

        if (!preferences.isEmpty()) {
            prompt.append("USER PREFERENCES:\n")
                .append(preferences.stream()
                    .collect(Collectors.joining("\n", "- ", "")))
                .append("\n\n");
        }

        prompt.append("Please evaluate the final answer against the original request")
            .append(preferences.isEmpty() ? "." : " and user preferences.");

        return prompt.toString();
    }
}
// end::all[]
