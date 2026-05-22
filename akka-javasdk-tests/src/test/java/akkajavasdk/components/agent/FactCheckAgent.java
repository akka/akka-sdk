/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "fact-check-agent")
public class FactCheckAgent extends Agent {

  public record FactCheckRequest(String claim, int confidence) {}

  public record FactCheckResponse(String verdict, boolean verified) {}

  public Effect<FactCheckResponse> checkFact(FactCheckRequest request) {
    return effects()
        .systemMessage("You are a fact checker.")
        .userMessage(
            "Check claim '" + request.claim() + "' with confidence " + request.confidence())
        .map(raw -> new FactCheckResponse(raw, true))
        .thenReply();
  }
}
