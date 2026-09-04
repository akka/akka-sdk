/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.annotations.Component;

/**
 * The agent behind {@link Judge#agent}: puts one question to a model and reads back a {@link
 * Judge.Verdict}.
 *
 * <p>Names no model, so it resolves {@code akka.javasdk.agent.model-provider} from the consumer's
 * own config; the model, the provider and the cost stay theirs. A suite that wants no provider
 * registers a {@code TestModelProvider} for this class, the same way it mocks the agent under test.
 *
 * <p>Memory is off. Each judgement stands alone: a judge that remembered its last answers would
 * drift toward them, and two runs in a different order would then disagree for reasons that have
 * nothing to do with the agent being judged.
 *
 * <p>This component is on the consumer's test classpath, so it registers when their TestKit boots
 * and is absent from their deployed service.
 */
@Component(id = "eval-judge")
public class JudgeAgent extends Agent {

  /**
   * @param instructions the rubric: what to decide and the shape to answer in
   * @param material the evidence being judged, rendered as text
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
