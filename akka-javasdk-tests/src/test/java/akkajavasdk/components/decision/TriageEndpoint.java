/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.decision;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.decision.ChoiceAnswer;
import akka.javasdk.decision.DecisionModelClient;
import akka.javasdk.decision.Question;
import akka.javasdk.decision.ScoreAnswer;
import akka.javasdk.decision.YesNoAnswer;

@HttpEndpoint("/triage")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TriageEndpoint {

  public record Ticket(String subject, String body) {}

  public record TriageResult(
      String team, double severity, double urgentProbability, String model) {}

  public static final Question<ChoiceAnswer> ROUTE =
      Question.choice("Which team should handle this ticket?")
          .option("billing", "Payments, invoicing, refunds")
          .option("technical", "Bugs, outages, integrations")
          .build("route");

  public static final Question<ScoreAnswer> SEVERITY =
      Question.score("How severe is the reported issue?")
          .levels(
              "Cosmetic, no impact on function",
              "Broken, but a workaround exists",
              "Blocking, no workaround")
          .build();

  public static final Question<YesNoAnswer> URGENT =
      Question.yesNo("Does the customer need a reply today?")
          .whenYes("The customer states a deadline of today or earlier")
          .whenNo("There is no deadline requirement")
          .build("urgent");

  private final DecisionModelClient decisionModelClient;

  public TriageEndpoint(DecisionModelClient decisionModelClient) {
    this.decisionModelClient = decisionModelClient;
  }

  @Post
  public TriageResult triage(Ticket ticket) {
    var decision = decisionModelClient.state(ticket).questions(ROUTE, SEVERITY, URGENT).invoke();
    return new TriageResult(
        decision.answer(ROUTE).selected(),
        decision.answer(SEVERITY).value(),
        decision.answer(URGENT).probability(),
        decision.model());
  }
}
