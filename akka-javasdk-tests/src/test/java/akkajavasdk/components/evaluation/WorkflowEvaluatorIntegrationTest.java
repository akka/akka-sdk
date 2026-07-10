/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.evaluation.Subject;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration test of the full workflow-evaluator flow: an agent interaction (agent model stubbed
 * with {@link TestModelProvider}) fires a trigger for the bound {@link
 * ResponseQualityWorkflowEvaluator}, which runs its evaluation as a workflow — fetching the
 * transcript in one step and judging it with an LLM-as-judge agent (also stubbed) in another —
 * until the verdict is recorded.
 */
@ExtendWith(Junit5LogCapturing.class)
public class WorkflowEvaluatorIntegrationTest extends TestKitSupport {

  private final TestModelProvider agentModel = new TestModelProvider();
  private final TestModelProvider judgeModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(EvaluatedAgent.class, agentModel)
        .withModelProvider(QualityJudge.class, judgeModel)
        .withAdditionalConfig(
            """
            akka.javasdk.evaluation.evaluators.response-quality-workflow-evaluator {
              agents {
                wf-evaluated-agent { trigger = interaction }
              }
            }
            """);
  }

  @BeforeEach
  public void beforeEach() {
    ResponseQualityWorkflowEvaluator.clearCompleted();
  }

  @AfterEach
  public void afterEach() {
    agentModel.reset();
    judgeModel.reset();
  }

  @Test
  public void runsMultiStepEvaluationForBoundAgentInteraction() {
    agentModel.fixedResponse("You can reset your password under account settings.");
    judgeModel.fixedResponse(
        """
        { "passed": true, "score": 0.9, "reason": "clear and helpful" }
        """
            .stripIndent());

    String answer =
        componentClient
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(EvaluatedAgent::ask)
            .invoke("How do I reset my password?");
    assertThat(answer).isNotBlank();

    Awaitility.await()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var completed = ResponseQualityWorkflowEvaluator.completedEvaluations();
              assertThat(completed).hasSize(1);

              var result = completed.values().iterator().next();
              assertThat(result.subject()).isInstanceOf(Subject.AgentInteraction.class);
              assertThat(result.subject().agentComponentId()).isEqualTo("wf-evaluated-agent");
              assertThat(result.evaluationId()).isNotBlank();

              var evaluation = result.evaluation();
              assertThat(evaluation.passed()).isTrue();
              assertThat(evaluation.score()).hasValue(0.9);
              assertThat(evaluation.explanation()).isEqualTo("clear and helpful");
            });
  }

  @Test
  public void recordsFailingVerdictFromTheJudge() {
    agentModel.fixedResponse("I don't know, figure it out yourself.");
    judgeModel.fixedResponse(
        """
        { "passed": false, "score": 0.1, "reason": "dismissive and unhelpful" }
        """
            .stripIndent());

    String answer =
        componentClient
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(EvaluatedAgent::ask)
            .invoke("How do I export my data?");
    assertThat(answer).isNotBlank();

    Awaitility.await()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var completed = ResponseQualityWorkflowEvaluator.completedEvaluations();
              assertThat(completed).hasSize(1);

              var evaluation = completed.values().iterator().next().evaluation();
              assertThat(evaluation.passed()).isFalse();
              assertThat(evaluation.explanation()).isEqualTo("dismissive and unhelpful");
            });
  }
}
