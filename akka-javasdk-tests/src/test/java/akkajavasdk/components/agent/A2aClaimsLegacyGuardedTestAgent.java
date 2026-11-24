/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.RemoteA2aAgent;
import akka.javasdk.annotations.Component;

/** Remote A2A agent next to a legacy model-request guardrail, which selects another effect path. */
@Component(id = "a2a-legacy-guarded-test-agent")
public class A2aClaimsLegacyGuardedTestAgent extends Agent {
  public record SomeResponse(String response) {}

  public Effect<SomeResponse> ask(String question) {
    return effects()
        .systemMessage("You are a helpful assistant")
        .a2aAgents(RemoteA2aAgent.named("claims").withAllowedSkills("lookup-claim"))
        .userMessage(question)
        .map(SomeResponse::new)
        .thenReply();
  }
}
