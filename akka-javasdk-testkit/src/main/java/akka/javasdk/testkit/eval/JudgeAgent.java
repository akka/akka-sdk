/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;

/**
 * The agent behind {@link Judge#agent}: sends the model one assessment and reads back a {@link
 * Judge.Verdict}.
 *
 * <p>Uses the model provider configured in {@code akka.javasdk.agent.model-provider}, or the one at
 * the configuration path the request names. A provider registered for this class in the TestKit
 * settings takes precedence over both, so to run without a provider register a {@code
 * TestModelProvider} for this class as for any other agent.
 *
 * <p>Memory is disabled, so each judgement stands alone.
 *
 * <p>The component is registered when the TestKit starts. It is not part of the deployed service.
 */
@Component(id = "eval-judge")
public class JudgeAgent extends Agent {

  /**
   * @param systemMessage what to decide and the reply format
   * @param userMessage the criterion and the evidence, as text
   * @param modelConfigPath the configuration path of the model to ask; empty for the configured
   *     default
   */
  public record Request(String systemMessage, String userMessage, String modelConfigPath) {}

  public Effect<Judge.Verdict> assess(Request request) {
    if (request == null
        || request.systemMessage() == null
        || request.userMessage() == null
        || request.modelConfigPath() == null) {
      return effects().error("systemMessage, userMessage and modelConfigPath are required");
    }
    var effect = effects().memory(MemoryProvider.none());
    if (!request.modelConfigPath().isEmpty()) {
      effect = effect.model(ModelProvider.fromConfig(request.modelConfigPath()));
    }
    return effect
        .systemMessage(request.systemMessage())
        .userMessage(request.userMessage())
        .responseConformsTo(Judge.Verdict.class)
        .thenReply();
  }
}
