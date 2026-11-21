/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.RemoteA2aAgent;
import akka.javasdk.annotations.Component;
import java.util.Set;

/** Offers one skill of the remote A2A agent named {@code claims} to the model. */
@Component(id = "remote-a2a-tool-test-agent")
public class RemoteA2aToolTestAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects()
        .systemMessage("You answer questions about insurance claims.")
        .a2aAgents(RemoteA2aAgent.named("claims").withAllowedSkills(Set.of("check-claim")))
        .userMessage(question)
        .thenReply();
  }
}
