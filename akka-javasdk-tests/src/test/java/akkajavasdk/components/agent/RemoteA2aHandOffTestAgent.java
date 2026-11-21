/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.RemoteA2aAgent;
import akka.javasdk.annotations.Component;
import java.time.Duration;

/**
 * Offers the remote A2A agent named {@code slow} to the model, and hands its tasks off when they
 * are still working after one second.
 */
@Component(id = "remote-a2a-hand-off-test-agent")
public class RemoteA2aHandOffTestAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects()
        .systemMessage("You answer questions about insurance claims.")
        .a2aAgents(
            RemoteA2aAgent.named("slow").handOffTasksStillWorkingAfter(Duration.ofSeconds(1)))
        .userMessage(question)
        .thenReply();
  }
}
