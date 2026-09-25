/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Judgment;
import akka.javasdk.agent.Question;
import akka.javasdk.annotations.Component;

@Component(id = "judgment-agent", name = "Judgment Agent", description = "Triages tickets")
public class SomeJudgmentAgent extends Agent {

  public Effect<Judgment> triage(String ticket) {
    return effects()
        .judgment()
        .state(ticket)
        .question(
            "route",
            Question.choice("Which team should handle this?")
                .option("billing", "Payments, invoicing, refunds")
                .option("technical", "Bugs, outages, integrations"))
        .question(
            "severity", Question.score("How severe is this?", "Low", "Medium", "High", "Critical"))
        .question(
            "urgent", Question.yesNo("Does this need a reply today?", "Time-sensitive", "Can wait"))
        .thenReply();
  }
}
