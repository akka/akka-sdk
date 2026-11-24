/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.subscriptions;

import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.consumer.MessageContext;
import akka.javasdk.ledger.FlowRecord;
import akka.javasdk.ledger.GuardrailDecisionRecord;
import akka.javasdk.ledger.InteractionRecord;

public class LedgerTestModels {

  @Consume.FromLedger
  public static class InteractionAndGuardrailConsumer extends Consumer {
    public InteractionRecord interaction;
    public GuardrailDecisionRecord guardrailDecision;

    public Effect onInteraction(InteractionRecord record) {
      interaction = record;
      return effects().done();
    }

    public Effect onGuardrailDecision(GuardrailDecisionRecord record) {
      guardrailDecision = record;
      return effects().done();
    }
  }

  @Consume.FromLedger
  public static class AllRecordsConsumer extends Consumer {
    public FlowRecord record;
    public MessageContext context;

    public Effect onRecord(FlowRecord record) {
      this.record = record;
      context = messageContext();
      return effects().done();
    }
  }

  @Consume.FromLedger
  public static class MixedConsumer extends Consumer {
    public FlowRecord record;
    public InteractionRecord interaction;

    public Effect onRecord(FlowRecord record) {
      this.record = record;
      return effects().done();
    }

    public Effect onInteraction(InteractionRecord record) {
      interaction = record;
      return effects().done();
    }
  }

  @Consume.FromLedger
  @Consume.StartFrom(latest = true)
  public static class StartFromLatestLedgerConsumer extends Consumer {
    public FlowRecord record;

    public Effect onRecord(FlowRecord record) {
      this.record = record;
      return effects().done();
    }
  }
}
