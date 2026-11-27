package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.ledger.FlowRecord;

@Component(id = "consumer-from-ledger-service-stream")
@Consume.FromLedger
@Produce.ServiceStream(id = "records")
public class ConsumerFromLedgerServiceStream extends Consumer {

  public Effect onRecord(FlowRecord record) {
    return effects().produce(record.recordId());
  }
}
