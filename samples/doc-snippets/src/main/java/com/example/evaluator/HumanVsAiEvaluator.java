package com.example.evaluator;

// tag::all[]
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.EvaluationResult;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;
import java.util.Locale;

@ComponentId("human-vs-ai-evaluator")
@AgentDescription(
  name = "Human vs AI Evaluator Agent",
  description = """
  An agent that acts as an LLM judge to evaluate that the human ground
  truth matches the AI generated answer.
  """,
  role = "evaluator"
)
public class HumanVsAiEvaluator extends Agent { // <1>

  public record EvaluationRequest(String question, String humanAnswer, String aiAnswer) {} // <2>

  public record Result(String explanation, String label) implements EvaluationResult { // <3>
    public boolean passed() {
      if (label == null) throw new IllegalArgumentException(
        "Model response must include label field"
      );

      return switch (label.toLowerCase(Locale.ROOT)) {
        case "correct" -> true;
        case "incorrect" -> false;
        default -> throw new IllegalArgumentException(
          "Unknown evaluation label [" + label + "]"
        );
      };
    }
  }

  private static final String SYSTEM_MESSAGE = // <4>
    """
    You are comparing a human ground truth answer from an expert to an answer from
    an AI model. Your goal is to determine if the AI answer correctly matches, in
    substance, the human answer.

    Compare the [AI answer] to the [Human ground truth answer]. First, write out in a
    step by step manner an EXPLANATION to show how to determine if the AI Answer is
    relevant or irrelevant. Avoid simply stating the correct answer at the
    outset. You are then going to respond with a LABEL (a single word evaluation).
    If the AI correctly answers the question as compared to the human answer, then
    the AI answer LABEL is "correct". If the AI answer is longer but contains the
    main idea of the Human answer please answer LABEL "correct". If the AI answer
    diverges or does not contain the main idea of the human answer, please answer
    LABEL "incorrect".

    Your response must be a single JSON object with the following fields:
    - "explanation": An explanation of your reasoning for why the label is "correct" or "incorrect"
    - "label": A string, either "correct" or "incorrect".
    """.stripIndent();

  private static final String USER_MESSAGE_TEMPLATE =
    """
    [Question]
    ************
    %s
    ************
    [Human ground truth answer]
    ************
    %s
    ************
    [AI Answer]
    ************
    %s
    ************
    """.stripIndent();

  public Effect<Result> evaluate(EvaluationRequest req) { // <5>
    String evaluationPrompt = USER_MESSAGE_TEMPLATE.formatted(
      req.question,
      req.humanAnswer,
      req.aiAnswer
    );

    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .memory(MemoryProvider.none())
      .userMessage(evaluationPrompt)
      .responseConformsTo(Result.class)
      .map(result -> {
        // make sure it's a valid label in the result, otherwise it will throw an exception
        result.passed(); // <6>
        return result;
      })
      .thenReply();
  }
}
// end::all[]
