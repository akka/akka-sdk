package demo.multiagent.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@ComponentId("tool-call-evaluator-agent")
public class ToolCallEvaluatorAgent extends Agent {

  public record EvaluationRequest(
      String userRequest,
      List<String> toolDefinitions,
      List<String> usedTools
  ) {}

  public record EvaluationResult(String evaluation, String justification) {
    public boolean ok() {
      return switch (evaluation.toLowerCase(Locale.ROOT)) {
        case "correct" -> true;
        case "incorrect" -> false;
        default -> throw new IllegalArgumentException("Unknown evaluation result [" + evaluation + "]");
      };
    }
  }

  private static final String SYSTEM_MESSAGE =
    """
    You are an expert evaluator for an AI agent. Your task is to determine if the
    [LLM's requested tools] are correct to address the [User request] given the [Available tools].
    
    Based on the user request and the available tools, was the agent's action a correct
    and logical choice?
    
    Your response must be a single JSON object with the following fields:
    - "evaluation": A string, either "Correct" or "Incorrect".
    - "justification": <An explanation of your reasoning for why "Correct" or "Incorrect"
    }

    Do not include any explanations or text outside of the JSON structure.
    """.stripIndent();

  private static final String USER_MESSAGE =
    """
    [User request]
    %s

    [Available tools]
    %s
    
    [LLM's requested tools]
    %s
    """.stripIndent();

  public Effect<EvaluationResult> evaluate(EvaluationRequest request) {
    // FIXME we would need to capture available tools, with descriptions, perhaps even as full json representation
    var toolDefinitions = request.toolDefinitions.stream().collect(Collectors.joining("- ", "\n", ""));
    var usedTools = request.usedTools.stream().collect(Collectors.joining("- ", "\n", ""));
    String evaluationPrompt = USER_MESSAGE.formatted(request.userRequest, toolDefinitions, usedTools);

    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(evaluationPrompt)
      .responseConformsTo(EvaluationResult.class)
      .thenReply();
  }

}
