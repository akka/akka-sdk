package demo.multiagent.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.EvaluationResult;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.PromptTemplate;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;

import java.util.Locale;

@ComponentId(SummarizationEvaluator.COMPONENT_ID)
@AgentDescription(
  name = "Summarization Evaluator Agent",
  description = """
  An agent that acts as an LLM judge to evaluate a summarization task.
  """,
  role = "evaluator"
)
public class SummarizationEvaluator extends Agent {
  private final ComponentClient componentClient;

  public SummarizationEvaluator(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record EvaluationRequest(String document, String summary) {}

  public record Result(String explanation, String label) implements EvaluationResult {
    public boolean passed() {
      return switch (label.toLowerCase(Locale.ROOT)) {
        case "good" -> true;
        case "bad" -> false;
        default -> throw new IllegalArgumentException(
          "Unknown evaluation label [" + label + "]"
        );
      };
    }
  }

  static final String COMPONENT_ID = "summarization-evaluator";
  private static final String SYSTEM_MESSAGE_PROMPT_ID = COMPONENT_ID + ".system";
  private static final String USER_MESSAGE_PROMPT_ID = COMPONENT_ID + ".user";

  private static final String INIT_SYSTEM_MESSAGE =
    """
    You are comparing the summary text and it's original document and trying to determine
    if the summary is good.

    Compare the [Summary] to the [Original Document]. First, write out in a step by step manner
    an EXPLANATION to show how to determine if the Summary is comprehensive, concise, coherent, and
    independent relative to the Original Document. Avoid simply stating the correct answer at the
    outset. Your response LABEL must be a single word, either "good" or "bad", and should not contain
    any text or characters aside from that. "bad" means that the Summary is not comprehensive, concise,
    coherent, and independent relative to the Original Document. "good" means the Summary is
    comprehensive, concise, coherent, and independent relative to the Original Document.

    Your response must be a single JSON object with the following fields:
    - "explanation": An explanation of your reasoning for why the label is "good" or "bad"
    - "label": A string, either "good" or "bad".
    """.stripIndent();

  private static final String USER_MESSAGE_TEMPLATE =
    """
    [Summary]
    ************
    {output}
    ************
    [Original Document]
    ************
    {input}
    ************
    """.stripIndent();

  public Effect<Result> evaluate(EvaluationRequest req) {
    String evaluationPrompt = buildEvaluationPrompt(req);

    return effects()
      .model(modelProvider())
      .systemMessage(INIT_SYSTEM_MESSAGE)
      .memory(MemoryProvider.none())
      .userMessage(evaluationPrompt)
      .responseConformsTo(Result.class)
      .thenReply();
  }

  private ModelProvider modelProvider() {
    return ModelProvider.fromConfig("akka.javasdk.agent.evaluators." + COMPONENT_ID + ".model-provider" );
  }

  private String buildEvaluationPrompt(EvaluationRequest req) {
    return prompt(USER_MESSAGE_PROMPT_ID, USER_MESSAGE_TEMPLATE).formatted(req.summary, req.document);
  }

  private String prompt(String promptId, String initMessage) {
    return componentClient.forEventSourcedEntity(promptId)
        .method(PromptTemplate::getOptional)
        .invoke().orElseGet(() -> initPrompt(promptId, initMessage));
  }

  private String initPrompt(String promptId, String initMessage) {
    componentClient.forEventSourcedEntity(promptId)
        .method(PromptTemplate::init)
        .invoke(initMessage);
    return initMessage;
  }
}
