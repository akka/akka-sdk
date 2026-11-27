/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.RemoteA2aAgent;
import akka.javasdk.annotations.Component;

@Component(id = "a2a-streaming-test-agent")
public class A2aClaimsStreamingTestAgent extends Agent {

  public StreamEffect ask(String question) {
    return streamEffects()
        .systemMessage("You are a helpful assistant")
        .a2aAgents(RemoteA2aAgent.named("claims").withAllowedSkills("lookup-claim"))
        .userMessage(question)
        .thenReply();
  }
}
