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
 * An agent that acts as an LLM judge to evaluate whether an output contains information not
 * available in the reference text given an input question.
 *
 * <p>Model provider is defined in configuration {@code
 * akka.javasdk.agent.evaluators.hallucination-evaluator.model-provider}, which by default is the
 * same as the default model provider.
 *
 * <p>The system and user message prompts are loaded from {@link PromptTemplate} with id {@code
 * hallucination-evaluator.system} and {@code hallucination-evaluator.user} respectively. Default
 * prompts are used if these are not defined. The prompts can be initialized or updated with the
 * {@link PromptTemplate} entity.
 */
@Component(id = HallucinationEvaluator.COMPONENT_ID)
@AgentDescription(
    name = "Hallucination Evaluator Agent",
    description =
        """
        An agent that acts as an LLM judge to evaluate whether an output contains information
        not available in the reference text given an input question.
        """,
    role = "evaluator")
public class HallucinationEvaluator extends LlmAsJudge {

  public HallucinationEvaluator(ComponentClient componentClient, Config config) {
    super(COMPONENT_ID, componentClient, config);
  }

  public record EvaluationRequest(String query, String referenceText, String answer) {}

  public record Result(String explanation, String label) implements EvaluationResult {
    public boolean passed() {
      if (label == null)
        throw new IllegalArgumentException("Model response must include label field");

      return switch (label.toLowerCase(Locale.ROOT)) {
        case "hallucinated" -> false;
        case "factual" -> true;
        default -> throw new IllegalArgumentException("Unknown evaluation label [" + label + "]");
      };
    }
  }

  static final String COMPONENT_ID = "hallucination-evaluator";
  private static final String SYSTEM_MESSAGE_PROMPT_ID = COMPONENT_ID + ".system";
  private static final String USER_MESSAGE_PROMPT_ID = COMPONENT_ID + ".user";

  private static final String SYSTEM_MESSAGE =
      // tag::prompt[]
      """
In this task, you will be presented with a [Query], a [Reference text] and an [Answer].
The answer is generated to the question based on the reference text. The answer may contain
false information. You must use the reference text to determine if the answer to the question
contains false information, if the answer is a hallucination of facts. Your objective is to
determine whether the answer text contains factual information and is not a hallucination.
A 'hallucination' refers to an answer that is not based on the reference text or assumes
information that is not available in the reference text.

Is the answer factual or hallucinated based on the query and reference text?

Please read the query, reference text and answer carefully, then write out in a step by step manner
an EXPLANATION to show how to determine if the answer is "factual" or "hallucinated". Avoid simply
stating the correct answer at the outset. Your response LABEL should be a single word: either
"factual" or "hallucinated", and it should not include any other text or characters. "hallucinated"
indicates that the answer provides factually inaccurate information to the query based on the
reference text. "factual" indicates that the answer to the question is correct relative to the
reference text, and does not contain made up information.

Your response must be a single JSON object with the following fields:
- "explanation": An explanation of your reasoning for why the label is "factual" or "hallucinated"
- "label": A string, either factual" or "hallucinated".
"""
          // end::prompt[]
          .stripIndent();

  private static final String USER_MESSAGE_TEMPLATE =
      """
      [Query]
      ************
      %s
      ************
      [Reference text]
      ************
      %s
      ************
      [Answer]
      ************
      %s
      ************
      """
          .stripIndent();

  public Effect<Result> evaluate(EvaluationRequest req) {
    String evaluationPrompt =
        prompt(USER_MESSAGE_PROMPT_ID, USER_MESSAGE_TEMPLATE)
            .formatted(req.query, req.referenceText, req.answer);

    return effects()
        .model(modelProvider())
        .systemMessage(prompt(SYSTEM_MESSAGE_PROMPT_ID, SYSTEM_MESSAGE))
        .memory(MemoryProvider.none())
        .userMessage(evaluationPrompt)
        .responseConformsTo(Result.class)
        .map(
            result -> {
              // make sure it's a valid label in the result, otherwise it will throw an exception
              result.passed();
              return result;
            })
        .thenReply();
  }
}
