/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.judgment;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.judgment.ChoiceAnswer;
import akka.javasdk.judgment.JudgmentClient;
import akka.javasdk.judgment.Question;
import akka.javasdk.judgment.ScoreAnswer;
import akka.javasdk.judgment.YesNoAnswer;

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

  private final JudgmentClient judgmentClient;

  public TriageEndpoint(JudgmentClient judgmentClient) {
    this.judgmentClient = judgmentClient;
  }

  @Post
  public TriageResult triage(Ticket ticket) {
    var judgment = judgmentClient.state(ticket).questions(ROUTE, SEVERITY, URGENT).invoke();
    return new TriageResult(
        judgment.answer(ROUTE).selected(),
        judgment.answer(SEVERITY).value(),
        judgment.answer(URGENT).probability(),
        judgment.model());
  }
}
