package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-from-ledger-wrong-parameter")
@Consume.FromLedger
public class ConsumerFromLedgerWrongParameter extends Consumer {

  public Effect onRecord(String record) {
    return effects().done();
  }
}
