/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.RemoteA2aAgent;
import akka.javasdk.annotations.Component;

/** Offers a remote A2A agent whose Agent Card cannot be read. */
@Component(id = "remote-a2a-unreadable-card-test-agent")
public class RemoteA2aUnreadableCardTestAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects()
        .systemMessage("You answer questions about insurance claims.")
        .a2aAgents(RemoteA2aAgent.named("unreachable"))
        .userMessage(question)
        .thenReply();
  }
}
