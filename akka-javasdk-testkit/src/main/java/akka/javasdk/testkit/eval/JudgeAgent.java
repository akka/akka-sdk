/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.annotations.Component;

/**
 * The agent behind {@link Judge#agent}: asks the model one question and reads back a {@link
 * Judge.Verdict}.
 *
 * <p>Uses the model provider configured in {@code akka.javasdk.agent.model-provider}. To run
 * without a provider, register a {@code TestModelProvider} for this class as for any other agent.
 *
 * <p>Memory is disabled, so each judgement stands alone.
 *
 * <p>The component is registered when the TestKit starts. It is not part of the deployed service.
 */
@Component(id = "eval-judge")
public class JudgeAgent extends Agent {

  /**
   * @param instructions the system message: what to decide and the reply format
   * @param material the criterion and the evidence, as text
   */
  public record Request(String instructions, String material) {}

  public Effect<Judge.Verdict> assess(Request request) {
    if (request == null || request.instructions() == null || request.material() == null) {
      return effects().error("instructions and material are required");
    }
    return effects()
        .memory(MemoryProvider.none())
        .systemMessage(request.instructions())
        .userMessage(request.material())
        .responseConformsTo(Judge.Verdict.class)
        .thenReply();
  }
}
