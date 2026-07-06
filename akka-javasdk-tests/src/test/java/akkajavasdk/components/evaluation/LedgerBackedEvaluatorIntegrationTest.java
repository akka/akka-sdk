/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Full-integration test of the ledger client: drives a real agent interaction through the embedded
 * runtime, which records it and — because {@link LedgerBackedEvaluator} is bound to {@link
 * LedgerEvalAgent} with {@code trigger = interaction} — samples it, enqueues an evaluation trigger,
 * and runs the evaluator. The evaluator fetches the actual interaction via the runtime-injected
 * {@link akka.javasdk.ledger.LedgerClient}, exercising the real ledger query end-to-end (no
 * seeding).
 */
@ExtendWith(Junit5LogCapturing.class)
public class LedgerBackedEvaluatorIntegrationTest extends TestKitSupport {

  private final TestModelProvider agentModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(LedgerEvalAgent.class, agentModel);
  }

  @BeforeEach
  public void clearProbe() {
    LedgerEvalProbe.clear();
  }

  @AfterEach
  public void reset() {
    agentModel.reset();
    LedgerEvalProbe.clear();
  }

  @Test
  public void triggeredEvaluatorFetchesRealInteractionFromLedger() {
    agentModel.whenMessage(question -> question.equals("What is 2+2?")).reply("The answer is 4.");

    String reply =
        componentClient
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(LedgerEvalAgent::ask)
            .invoke("What is 2+2?");
    assertThat(reply).isEqualTo("The answer is 4.");

    // the runtime records the interaction, samples it, enqueues a trigger, and runs the bound
    // evaluator, which fetches the real interaction via the injected ledger client
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(LedgerEvalProbe.all()).isNotEmpty());

    // exactly one interaction was recorded and evaluated: one agent request produces one
    // interaction, and the probe is keyed by interaction id so trigger redelivery collapses to it
    assertThat(LedgerEvalProbe.all()).hasSize(1);

    InteractionRecord record = LedgerEvalProbe.all().iterator().next();
    assertThat(record.agentComponentId()).isEqualTo("ledger-eval-agent");
    assertThat(record.userText()).isEqualTo("What is 2+2?");
    assertThat(record.finalAssistantText()).isEqualTo("The answer is 4.");
    assertThat(record.isFlowInteraction()).isFalse();
    assertThat(record.failed()).isFalse();
    assertThat(record.transcript()).contains("Assistant: The answer is 4.");
  }
}
