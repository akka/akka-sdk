/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.evaluator;

import akka.javasdk.agent.EvaluationResult;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.PromptTemplate;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import com.typesafe.config.Config;
import java.util.Locale;

/**
 * An agent that acts as an LLM judge to evaluate a summarization task.
 *
 * <p>Model provider is defined in configuration {@code
 * akka.javasdk.agent.evaluators.summarization-evaluator.model-provider}, which by default is the
 * same as the default model provider.
 *
 * <p>The system and user message prompts are loaded from {@link PromptTemplate} with id {@code
 * summarization-evaluator.system} and {@code summarization-evaluator.user} respectively. Default
 * prompts are used if these are not defined. The prompts can be initialized or updated with the
 * {@link PromptTemplate} entity.
 */
@Component(id = SummarizationEvaluator.COMPONENT_ID)
@AgentDescription(
    name = "Summarization Evaluator Agent",
    description =
        """
        An agent that acts as an LLM judge to evaluate a summarization task.
        """,
    role = "evaluator")
public class SummarizationEvaluator extends LlmAsJudge {

  public SummarizationEvaluator(ComponentClient componentClient, Config config) {
    super(COMPONENT_ID, componentClient, config);
  }

  public record EvaluationRequest(String document, String summary) {}

  record ModelResult(String explanation, String label) {
    Result toEvaluationResult() {
      if (label == null)
        throw new IllegalArgumentException("Model response must include label field");

      var passed =
          switch (label.toLowerCase(Locale.ROOT)) {
            case "good" -> true;
            case "bad" -> false;
            default ->
                throw new IllegalArgumentException("Unknown evaluation label [" + label + "]");
          };

      return new Result(explanation, passed);
    }
  }

  public record Result(String explanation, boolean passed) implements EvaluationResult {}

  static final String COMPONENT_ID = "summarization-evaluator";
  private static final String SYSTEM_MESSAGE_PROMPT_ID = COMPONENT_ID + ".system";
  private static final String USER_MESSAGE_PROMPT_ID = COMPONENT_ID + ".user";

  private static final String SYSTEM_MESSAGE =
      // tag::prompt[]
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
"""
          // end::prompt[]
          .stripIndent();

  private static final String USER_MESSAGE_TEMPLATE =
      """
      [Summary]
      ************
      %s
      ************
      [Original Document]
      ************
      %s
      ************
      """
          .stripIndent();

  public Effect<Result> evaluate(EvaluationRequest req) {
    String evaluationPrompt =
        prompt(USER_MESSAGE_PROMPT_ID, USER_MESSAGE_TEMPLATE).formatted(req.summary, req.document);

    return effects()
        .model(modelProvider())
        .systemMessage(prompt(SYSTEM_MESSAGE_PROMPT_ID, SYSTEM_MESSAGE))
        .memory(MemoryProvider.none())
        .userMessage(evaluationPrompt)
        .responseConformsTo(ModelResult.class)
        .map(ModelResult::toEvaluationResult)
        .thenReply();
  }
}
