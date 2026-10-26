/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.ledger.GuardrailDecisionRecord;
import akka.javasdk.ledger.InteractionMetadata;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.ModelConfig;
import akka.javasdk.testkit.TestLedgerClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class TestLedgerClientTest {

  private static InteractionRecord interaction(
      String id, String agentId, String sessionId, Instant timestamp) {
    var metadata =
        new InteractionMetadata(
            new ModelConfig("test", "test", "", -1.0, -1.0, -1, -1),
            Map.of(),
            timestamp,
            timestamp,
            InteractionMetadata.FinishReason.STOP);
    return new InteractionRecord(
        id,
        sessionId,
        agentId,
        Optional.empty(),
        metadata,
        "",
        List.of(),
        List.of(),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        timestamp);
  }

  private static GuardrailDecisionRecord decision(
      String id, String interactionId, String sessionId, Instant timestamp) {
    return new GuardrailDecisionRecord(
        id,
        "support-agent",
        interactionId,
        sessionId,
        Optional.empty(),
        "no-refunds",
        Optional.empty(),
        "policy",
        GuardrailDecisionRecord.Boundary.BEFORE_MODEL_CALL,
        GuardrailDecisionRecord.Decision.ALLOW,
        "",
        false,
        GuardrailDecisionRecord.FailMode.FAIL_CLOSED,
        Duration.ZERO,
        Optional.empty(),
        timestamp);
  }

  @Test
  public void listsTheInteractionsOfAnAgentInASessionNewestFirst() {
    var first = interaction("i1", "support-agent", "session-1", Instant.ofEpochSecond(1));
    var second = interaction("i2", "support-agent", "session-1", Instant.ofEpochSecond(2));
    var ledger =
        TestLedgerClient.create()
            .seed(first)
            .seed(second)
            .seed(interaction("i3", "support-agent", "session-2", Instant.ofEpochSecond(3)))
            .seed(interaction("i4", "other-agent", "session-1", Instant.ofEpochSecond(4)));

    assertEquals(List.of(second, first), ledger.getInteractions("support-agent", "session-1"));
    assertTrue(ledger.getInteractions("support-agent", "session-3").isEmpty());
  }

  @Test
  public void listsTheGuardrailDecisionsOfAnInteractionNewestFirst() {
    var first = decision("g1", "interaction-1", "session-1", Instant.ofEpochSecond(1));
    var second = decision("g2", "interaction-1", "session-1", Instant.ofEpochSecond(2));
    var ledger =
        TestLedgerClient.create()
            .seed(first)
            .seed(second)
            .seed(decision("g3", "interaction-2", "session-1", Instant.ofEpochSecond(3)));

    assertEquals(List.of(second, first), ledger.getGuardrailDecisions("interaction-1"));
    assertTrue(ledger.getGuardrailDecisions("interaction-3").isEmpty());
  }
}
