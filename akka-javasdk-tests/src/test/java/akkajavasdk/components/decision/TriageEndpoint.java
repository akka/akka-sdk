/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.decision;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.decision.Answer;
import akka.javasdk.decision.DecisionModelClient;
import akka.javasdk.decision.DecisionModelProvider;
import akka.javasdk.decision.Question;
import java.util.List;
import java.util.Map;

@HttpEndpoint("/triage")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TriageEndpoint {

  public record Ticket(String subject, String body) {}

  public record TriageResult(
      String team, double severity, double urgentProbability, String model) {}

  public static final Question<Answer.Choice> ROUTE =
      Question.choice("Which team should handle this ticket?")
          .option("billing", "Payments, invoicing, refunds")
          .option("technical", "Bugs, outages, integrations")
          .build("route");

  public static final Question<Answer.Score> SEVERITY =
      Question.score("How severe is the reported issue?")
          .levels(
              "Cosmetic, no impact on function",
              "Broken, but a workaround exists",
              "Blocking, no workaround")
          .build("severity");

  public static final Question<Answer.YesNo> URGENT =
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

  @Post("/failure-class")
  public String failureClass(Ticket ticket) {
    try {
      decisionModelClient.state(ticket).questions(ROUTE, SEVERITY, URGENT).invoke();
      return "no-failure";
    } catch (RuntimeException e) {
      return e.getClass().getSimpleName();
    }
  }

  // routes for calling a SystemOne stand-in server through the runtime

  public record StandInCall(String baseUrl, String state) {}

  public record ChoiceResult(
      String selected, double confidence, Map<String, Double> probabilities, String model) {}

  public record ScoreResult(double value, double confidence, List<Double> probabilities) {}

  public record YesNoResult(double probability) {}

  private static DecisionModelProvider.SystemOne standIn(String baseUrl) {
    return DecisionModelProvider.systemOne()
        .withApiKey("test-key")
        .withModelName("jev-latest")
        .withBaseUrl(baseUrl)
        .withMaxRetries(0);
  }

  @Post("/system-one/route")
  public ChoiceResult routeWithSystemOne(StandInCall call) {
    var decision =
        decisionModelClient
            .model(standIn(call.baseUrl()))
            .state(call.state())
            .questions(ROUTE)
            .invoke();
    var answer = decision.answer(ROUTE);
    return new ChoiceResult(
        answer.selected(), answer.confidence(), answer.probabilities(), decision.model());
  }

  @Post("/system-one/severity")
  public ScoreResult severityWithSystemOne(StandInCall call) {
    var decision =
        decisionModelClient
            .model(standIn(call.baseUrl()))
            .state(call.state())
            .questions(SEVERITY)
            .invoke();
    var answer = decision.answer(SEVERITY);
    return new ScoreResult(answer.value(), answer.confidence(), answer.probabilities());
  }

  @Post("/system-one/urgent")
  public YesNoResult urgentWithSystemOne(StandInCall call) {
    var decision =
        decisionModelClient
            .model(standIn(call.baseUrl()))
            .state(call.state())
            .questions(URGENT)
            .invoke();
    return new YesNoResult(decision.answer(URGENT).probability());
  }

  @Post("/system-one/failure-class")
  public String systemOneFailureClass(StandInCall call) {
    try {
      decisionModelClient
          .model(standIn(call.baseUrl()))
          .state(call.state())
          .questions(URGENT)
          .invoke();
      return "no-failure";
    } catch (RuntimeException e) {
      return e.getClass().getSimpleName();
    }
  }
}
