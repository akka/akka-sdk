/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.ledger;

import akka.javasdk.Metadata;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.ledger.GuardrailDecisionRecord;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** A ledger consumer that selects guardrail decisions only and stores each record it receives. */
@Component(id = "guardrail-decision-record-consumer")
@Consume.FromLedger
public class GuardrailDecisionRecordConsumer extends Consumer {

  /** A received record, with the metadata and the event subject of its message. */
  public record Received(
      GuardrailDecisionRecord record, Metadata metadata, Optional<String> eventSubject) {}

  public static final List<Received> receivedRecords = new CopyOnWriteArrayList<>();

  public Effect onGuardrailDecision(GuardrailDecisionRecord record) {
    receivedRecords.add(
        new Received(record, messageContext().metadata(), messageContext().eventSubject()));
    return effects().done();
  }
}
