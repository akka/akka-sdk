package demo.multiagent.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@ComponentId("hallucination-evaluator-agent")
public class HallucinationEvaluatorAgent extends Agent {

  public record EvaluationRequest(
      String userRequest,
      String aiResponse,
      String reference
  ) {}

  public record EvaluationResult(String evaluation, String justification) {
    public boolean ok() {
      return switch (evaluation.toLowerCase(Locale.ROOT)) {
        case "grounded" -> true;
        case "hallucination" -> false;
        default -> throw new IllegalArgumentException("Unknown evaluation result [" + evaluation + "]");
      };
    }
  }

  private static final String SYSTEM_MESSAGE =
    """
    You are a strict AI evaluator. Your objective is to determine if the provided [Answer] is fully
    supported by the [Reference text].
    
    A 'Hallucination' occurs when the [Answer] contains any information not explicitly found
    in the [Reference text]. The answer must be 'Grounded' in the source material. Do not use
    any external knowledge.
    
    Follow these steps to make your determination:
    1.  **Deconstruct:** Break down the [Answer] into individual claims or statements.
    2.  **Verify:** For each individual claim, scan the [Reference text] to see if you can find direct evidence that supports it.
    3.  **Conclude:** Based on your verification, decide if the entire [Answer] is grounded or contains a hallucination.
    
    After following these steps, provide your final response as a single JSON object with the required fields.
    - "evaluation": A string, either "Grounded" or "Hallucination".
    - "justification": A brief explanation for your decision. If the evaluation is hallucinated, quote the specific part of the output that is not supported by the source.
    """.stripIndent();

  private static final String USER_MESSAGE =
    """
    [User request]
    %s

    [Answer]
    %s
    
    [Reference text]
    %s
    """.stripIndent();

  // FIXME if reference text is knowledge (as in RAG) it might be better to include all of
  // user request and reference in one section, to match with how we recommend adding knowledge.


  public Effect<EvaluationResult> evaluate(EvaluationRequest request) {
    String evaluationPrompt = USER_MESSAGE.formatted(request.userRequest, request.aiResponse, request.reference);

    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(evaluationPrompt)
      .responseConformsTo(EvaluationResult.class)
      .thenReply();
  }

}
