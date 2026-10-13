/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

/** Named by no sanitizer, and has no role, so only the unscoped entries apply to it. */
@Component(id = "sanitizer-other-agent")
public class SanitizerOtherTestAgent extends Agent {

  public record SomeResponse(String response) {}

  public Effect<SomeResponse> query(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .map(SomeResponse::new)
        .thenReply();
  }
}
