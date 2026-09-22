/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Question;
import akka.javasdk.annotations.Component;

@Component(
    id = "judgment-structured-state-agent",
    name = "Judgment Structured State Agent",
    description = "Judges a structured ticket")
public class SomeJudgmentStructuredStateAgent extends Agent {

  public record Ticket(String subject, String body) {}

  public Effect<String> route(Ticket ticket) {
    return effects()
        .judgment()
        .state(ticket)
        .question(
            "route",
            Question.choice("Which team should handle this?").option("billing").option("technical"))
        .map(judgment -> judgment.choice("route").selected())
        .thenReply();
  }
}
