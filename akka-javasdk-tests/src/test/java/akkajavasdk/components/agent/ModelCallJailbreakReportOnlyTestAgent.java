/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "model-call-jailbreak-report-only-test-agent")
public class ModelCallJailbreakReportOnlyTestAgent extends Agent {
  public record SomeResponse(String response) {}

  public Effect<SomeResponse> ask(String question) {
    return effects()
        .systemMessage("You are a helpful assistant")
        .userMessage(question)
        .map(SomeResponse::new)
        .thenReply();
  }
}
