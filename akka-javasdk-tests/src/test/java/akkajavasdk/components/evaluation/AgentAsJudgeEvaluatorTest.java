/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.evaluation.Subject;
import akka.javasdk.testkit.EvaluatorResult;
import akka.javasdk.testkit.EvaluatorTestKit;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration test: an Evaluator calls a judge agent (LLM-as-judge) through a real component
 * client, with the judge's model stubbed via {@link TestModelProvider}.
 */
@ExtendWith(Junit5LogCapturing.class)
public class AgentAsJudgeEvaluatorTest extends TestKitSupport {

  private final TestModelProvider judgeModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(QualityJudge.class, judgeModel);
  }

  @AfterEach
  public void afterEach() {
    judgeModel.reset();
  }

  private Subject agentInteraction() {
    return new Subject.AgentInteraction("support-agent", "session-1", "writer-1", 3);
  }

  @Test
  public void recordsPassingJudgeVerdict() {
    judgeModel.fixedResponse(
        """
        { "passed": true, "score": 0.87, "reason": "clear and helpful" }
        """
            .stripIndent());

    EvaluatorTestKit<ConversationQualityEvaluator> testKit =
        EvaluatorTestKit.of(() -> new ConversationQualityEvaluator(componentClient));

    EvaluatorResult result = testKit.evaluate(agentInteraction(), "eval-1");

    assertThat(result.isRecord()).isTrue();
    assertThat(result.getEvaluations()).hasSize(1);
    var evaluation = result.getEvaluations().get(0);
    assertThat(evaluation.passed()).isTrue();
    assertThat(evaluation.score().orElseThrow()).isEqualTo(0.87);
    assertThat(evaluation.explanation()).isEqualTo("clear and helpful");
  }

  @Test
  public void recordsFailingJudgeVerdict() {
    judgeModel.fixedResponse(
        """
        { "passed": false, "score": 0.12, "reason": "unhelpful and off-topic" }
        """
            .stripIndent());

    EvaluatorTestKit<ConversationQualityEvaluator> testKit =
        EvaluatorTestKit.of(() -> new ConversationQualityEvaluator(componentClient));

    EvaluatorResult result = testKit.evaluate(agentInteraction(), "eval-2");

    assertThat(result.isRecord()).isTrue();
    var evaluation = result.getEvaluations().get(0);
    assertThat(evaluation.passed()).isFalse();
    assertThat(evaluation.explanation()).isEqualTo("unhelpful and off-topic");
  }
}
