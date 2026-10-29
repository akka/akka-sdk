/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

/**
 * An agent whose fail-closed response guardrail throws. The guardrail failure reaches the caller.
 */
@Component(id = "unhandled-throwing-guardrail-test-agent")
public class UnhandledThrowingGuardrailTestAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects().systemMessage("You are a helpful assistant").userMessage(question).thenReply();
  }
}
