package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.ledger.FlowRecord;

@Component(id = "consumer-from-ledger-and-topic")
@Consume.FromLedger
@Consume.FromTopic("records")
public class ConsumerFromLedgerAndTopic extends Consumer {

  public Effect onRecord(FlowRecord record) {
    return effects().done();
  }
}
