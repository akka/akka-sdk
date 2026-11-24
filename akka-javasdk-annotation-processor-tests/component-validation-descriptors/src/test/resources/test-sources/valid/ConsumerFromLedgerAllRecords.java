package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.ledger.FlowRecord;

@Component(id = "consumer-from-ledger-all-records")
@Consume.FromLedger
public class ConsumerFromLedgerAllRecords extends Consumer {

  public Effect onRecord(FlowRecord record) {
    return effects().done();
  }
}
