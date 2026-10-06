/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Binds {@link LedgerBackedEvaluator} to {@link LedgerEvalAgent} with {@code trigger-on-failure =
 * true}, so the runtime also triggers the evaluator for a failed interaction of the agent.
 */
@ExtendWith(Junit5LogCapturing.class)
public class FailedInteractionEvaluatorIntegrationTest extends TestKitSupport {

  private final TestModelProvider agentModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(LedgerEvalAgent.class, agentModel)
        .withAdditionalConfig(
            "akka.javasdk.evaluation.evaluators.ledger-eval-evaluator.agents.ledger-eval-agent"
                + ".trigger-on-failure = true");
  }

  @BeforeEach
  public void clearProbe() {
    agentModel.reset();
    LedgerEvalProbe.clear();
  }

  @AfterEach
  public void reset() {
    agentModel.reset();
    LedgerEvalProbe.clear();
  }

  @Test
  public void evaluatesFailedInteraction() {
    agentModel.whenMessage(msg -> true).failWith(new RuntimeException("agent model exploded"));

    String sessionId = UUID.randomUUID().toString();
    assertThatThrownBy(
        () ->
            componentClient
                .forAgent()
                .inSession(sessionId)
                .method(LedgerEvalAgent::ask)
                .invoke("What is 2+2?"));

    // a failed call returns no reply, so the interaction id comes from the record that the
    // evaluator fetched
    InteractionRecord record =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .until(
                () ->
                    LedgerEvalProbe.all().stream()
                        .filter(r -> r.sessionId().equals(sessionId))
                        .findFirst(),
                Optional::isPresent)
            .orElseThrow();

    assertThat(record.failed()).isTrue();
    assertThat(record.failure()).isPresent();

    List<EvaluationRecord> evaluations =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .until(
                () -> getLedgerClient().getEvaluations(record.interactionId()),
                found -> !found.isEmpty());
    assertThat(evaluations)
        .extracting(EvaluationRecord::evaluatorComponentId)
        .containsExactly("ledger-eval-evaluator");
  }
}
