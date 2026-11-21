/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.RemoteA2aAgent;
import akka.javasdk.annotations.Component;
import java.time.Duration;

/**
 * As {@link RemoteA2aHandOffTestAgent}, with session memory turned off, so a task still working
 * after the hand-off time has nowhere to write its outcome.
 */
@Component(id = "remote-a2a-hand-off-without-memory-test-agent")
public class RemoteA2aHandOffWithoutMemoryTestAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects()
        .systemMessage("You answer questions about insurance claims.")
        .memory(MemoryProvider.none())
        .a2aAgents(
            RemoteA2aAgent.named("slow").handOffTasksStillWorkingAfter(Duration.ofSeconds(1)))
        .userMessage(question)
        .thenReply();
  }
}
