package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.consumer.Consumer;

@Component(id = "consumer-from-ledger-delete-handler")
@Consume.FromLedger
public class ConsumerFromLedgerDeleteHandler extends Consumer {

  @DeleteHandler
  public Effect onDelete() {
    return effects().done();
  }
}
