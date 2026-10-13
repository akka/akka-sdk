/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.decision.ChoiceAnswer;
import akka.javasdk.decision.DecisionModelProvider;
import akka.javasdk.decision.Question;
import akka.javasdk.decision.ScoreAnswer;
import akka.javasdk.decision.YesNoAnswer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TestDecisionModelProviderTest {

  private static final Question<ChoiceAnswer> ROUTE =
      Question.choice("Which team?")
          .option("billing", "Payments")
          .option("technical", "Bugs")
          .option("sales", "New business")
          .build("route");

  private static final Question<ScoreAnswer> SEVERITY =
      Question.score("How severe?").levels("low", "medium", "high").build("severity");

  private static final Question<YesNoAnswer> URGENT = Question.yesNo("Urgent?").build("urgent");

  private final TestDecisionModelProvider provider = new TestDecisionModelProvider();

  private static DecisionModelProvider.Request request(Object state, Question<?>... questions) {
    return new DecisionModelProvider.Request(state, List.of(questions));
  }

  @Test
  public void answerWithProbabilitiesForEveryOptionAndLevel() {
    provider
        .whenState("ticket")
        .choice(ROUTE, "technical")
        .score(SEVERITY, 1.25)
        .yesNo(URGENT, 0.9);

    var decision = provider.decide(request("ticket", ROUTE, SEVERITY, URGENT));

    assertThat(decision.model()).isEqualTo(TestDecisionModelProvider.MODEL_NAME);
    var route = decision.answer(ROUTE);
    assertThat(route.selected()).isEqualTo("technical");
    assertThat(route.probabilities())
        .containsExactly(
            Map.entry("billing", 0.0), Map.entry("technical", 1.0), Map.entry("sales", 0.0));
    var severity = decision.answer(SEVERITY);
    assertThat(severity.value()).isEqualTo(1.25);
    assertThat(severity.probabilities()).containsExactly(0.0, 0.75, 0.25);
    assertThat(decision.answer(URGENT).probability()).isEqualTo(0.9);
  }

  @Test
  public void leaveOutAnswersForQuestionsNotAsked() {
    provider.always().choice(ROUTE, "billing").yesNo(URGENT, 0.1);

    var decision = provider.decide(request("anything", URGENT));

    assertThat(decision.answers()).containsOnlyKeys("urgent");
  }

  @Test
  public void useTheMostRecentMatchingRule() {
    provider.always().yesNo(URGENT, 0.1);
    provider
        .whenRequest(request -> request.state().toString().contains("today"))
        .yesNo(URGENT, 0.95);

    assertThat(provider.decide(request("reply today", URGENT)).answer(URGENT).probability())
        .isEqualTo(0.95);
    assertThat(provider.decide(request("no rush", URGENT)).answer(URGENT).probability())
        .isEqualTo(0.1);
  }

  @Test
  public void failWhenNoRuleMatches() {
    provider.whenState("ticket").yesNo(URGENT, 0.5);

    assertThatThrownBy(() -> provider.decide(request("other", URGENT)))
        .isInstanceOf(TestDecisionModelProvider.MissingDecisionResponseException.class)
        .hasMessageContaining("[urgent]");
  }

  @Test
  public void failWhenTheRuleDoesNotAnswerAQuestion() {
    provider.whenState("ticket").yesNo(URGENT, 0.5);

    assertThatThrownBy(() -> provider.decide(request("ticket", URGENT, ROUTE)))
        .isInstanceOf(TestDecisionModelProvider.MissingDecisionResponseException.class)
        .hasMessageContaining("[route]");
  }

  @Test
  public void failWithTheGivenException() {
    provider.whenState("ticket").failWith(new IllegalStateException("model down"));

    assertThatThrownBy(() -> provider.decide(request("ticket", URGENT)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("model down");
  }

  @Test
  public void rejectAnswersOutsideTheQuestion() {
    var rule = provider.always();
    assertThatThrownBy(() -> rule.choice(ROUTE, "marketing"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> rule.score(SEVERITY, 2.5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> rule.yesNo(URGENT, 1.5)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void resetRemovesAllRules() {
    provider.always().yesNo(URGENT, 0.5);
    provider.reset();

    assertThatThrownBy(() -> provider.decide(request("ticket", URGENT)))
        .isInstanceOf(TestDecisionModelProvider.MissingDecisionResponseException.class);
  }
}
