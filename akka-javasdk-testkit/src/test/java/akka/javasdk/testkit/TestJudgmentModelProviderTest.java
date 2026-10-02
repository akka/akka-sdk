/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.judgment.ChoiceAnswer;
import akka.javasdk.judgment.JudgmentModelProvider;
import akka.javasdk.judgment.Question;
import akka.javasdk.judgment.ScoreAnswer;
import akka.javasdk.judgment.YesNoAnswer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TestJudgmentModelProviderTest {

  private static final Question<ChoiceAnswer> ROUTE =
      Question.choice("Which team?")
          .option("billing", "Payments")
          .option("technical", "Bugs")
          .option("sales", "New business")
          .build("route");

  private static final Question<ScoreAnswer> SEVERITY =
      Question.score("How severe?").levels("low", "medium", "high").build("severity");

  private static final Question<YesNoAnswer> URGENT = Question.yesNo("Urgent?").build("urgent");

  private final TestJudgmentModelProvider provider = new TestJudgmentModelProvider();

  private static JudgmentModelProvider.Request request(Object state, Question<?>... questions) {
    return new JudgmentModelProvider.Request(state, List.of(questions));
  }

  @Test
  public void answerWithProbabilitiesForEveryOptionAndLevel() {
    provider
        .whenState("ticket")
        .choice(ROUTE, "technical")
        .score(SEVERITY, 1.25)
        .yesNo(URGENT, 0.9);

    var judgment = provider.judge(request("ticket", ROUTE, SEVERITY, URGENT));

    assertThat(judgment.model()).isEqualTo(TestJudgmentModelProvider.MODEL_NAME);
    var route = judgment.answer(ROUTE);
    assertThat(route.selected()).isEqualTo("technical");
    assertThat(route.probabilities())
        .containsExactly(
            Map.entry("billing", 0.0), Map.entry("technical", 1.0), Map.entry("sales", 0.0));
    var severity = judgment.answer(SEVERITY);
    assertThat(severity.value()).isEqualTo(1.25);
    assertThat(severity.probabilities()).containsExactly(0.0, 0.75, 0.25);
    assertThat(judgment.answer(URGENT).probability()).isEqualTo(0.9);
  }

  @Test
  public void leaveOutAnswersForQuestionsNotAsked() {
    provider.always().choice(ROUTE, "billing").yesNo(URGENT, 0.1);

    var judgment = provider.judge(request("anything", URGENT));

    assertThat(judgment.answers()).containsOnlyKeys("urgent");
  }

  @Test
  public void useTheMostRecentMatchingRule() {
    provider.always().yesNo(URGENT, 0.1);
    provider
        .whenRequest(request -> request.state().toString().contains("today"))
        .yesNo(URGENT, 0.95);

    assertThat(provider.judge(request("reply today", URGENT)).answer(URGENT).probability())
        .isEqualTo(0.95);
    assertThat(provider.judge(request("no rush", URGENT)).answer(URGENT).probability())
        .isEqualTo(0.1);
  }

  @Test
  public void failWhenNoRuleMatches() {
    provider.whenState("ticket").yesNo(URGENT, 0.5);

    assertThatThrownBy(() -> provider.judge(request("other", URGENT)))
        .isInstanceOf(TestJudgmentModelProvider.MissingJudgmentResponseException.class)
        .hasMessageContaining("[urgent]");
  }

  @Test
  public void failWhenTheRuleDoesNotAnswerAQuestion() {
    provider.whenState("ticket").yesNo(URGENT, 0.5);

    assertThatThrownBy(() -> provider.judge(request("ticket", URGENT, ROUTE)))
        .isInstanceOf(TestJudgmentModelProvider.MissingJudgmentResponseException.class)
        .hasMessageContaining("[route]");
  }

  @Test
  public void failWithTheGivenException() {
    provider.whenState("ticket").failWith(new IllegalStateException("model down"));

    assertThatThrownBy(() -> provider.judge(request("ticket", URGENT)))
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

    assertThatThrownBy(() -> provider.judge(request("ticket", URGENT)))
        .isInstanceOf(TestJudgmentModelProvider.MissingJudgmentResponseException.class);
  }
}
