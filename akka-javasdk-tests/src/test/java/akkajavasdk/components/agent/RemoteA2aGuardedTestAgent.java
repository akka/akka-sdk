/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Guardrail;
import akka.javasdk.agent.RemoteA2aAgent;
import akka.javasdk.annotations.Component;

/**
 * Offers every skill of the remote A2A agent named {@code claims} to the model. {@link
 * RemoteA2aDenyingToolGuard} guards its tool calls, and a denial becomes the reply.
 */
@Component(id = "remote-a2a-guarded-test-agent")
public class RemoteA2aGuardedTestAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects()
        .systemMessage("You answer questions about insurance claims.")
        .a2aAgents(RemoteA2aAgent.named("claims"))
        .userMessage(question)
        .onFailure(
            cause ->
                switch (cause) {
                  case Guardrail.GuardrailException e -> e.getMessage();
                  case RuntimeException e -> throw e;
                  default -> throw new RuntimeException(cause);
                })
        .thenReply();
  }
}
