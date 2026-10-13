/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

/**
 * A request-based agent whose interactions are recorded to the ledger and then evaluated by {@link
 * LedgerBackedEvaluator}, bound to it via {@code akka.javasdk.evaluation} config. Used by {@link
 * LedgerBackedEvaluatorIntegrationTest} to drive a real interaction end-to-end.
 */
@Component(id = "ledger-eval-agent")
public class LedgerEvalAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects().systemMessage("You are a calculator.").userMessage(question).thenReply();
  }
}
