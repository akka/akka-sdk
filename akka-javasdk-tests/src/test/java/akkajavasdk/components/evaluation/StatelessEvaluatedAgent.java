/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

/**
 * A simple agent whose interactions are evaluated by {@link ResponseQualityEvaluator}, bound
 * through configuration in the stateless-evaluator integration test.
 */
@Component(
    id = "stateless-evaluated-agent",
    name = "Stateless Evaluated Agent",
    description = "A support agent whose responses are evaluated by a stateless evaluator.")
public class StatelessEvaluatedAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects()
        .systemMessage("You are a helpful support agent.")
        .userMessage(question)
        .thenReply();
  }
}
