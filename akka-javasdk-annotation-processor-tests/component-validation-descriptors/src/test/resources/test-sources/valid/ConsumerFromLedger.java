package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.ledger.GuardrailDecisionRecord;
import akka.javasdk.ledger.InteractionRecord;

@Component(id = "consumer-from-ledger")
@Consume.FromLedger
@Consume.StartFrom(latest = true)
@Produce.ToTopic("records")
public class ConsumerFromLedger extends Consumer {

  public Effect onInteraction(InteractionRecord record) {
    return effects().produce(record.interactionId());
  }

  public Effect onGuardrailDecision(GuardrailDecisionRecord record) {
    return effects().produce(record.recordId());
  }
}
