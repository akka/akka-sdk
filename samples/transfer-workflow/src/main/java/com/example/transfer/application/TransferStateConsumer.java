package com.example.transfer.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import com.example.transfer.domain.TransferState;

// tag::workflow-consumer[]
@ComponentId("transfer-state-consumer")
@Consume.FromWorkflow(TransferWorkflow.class) // <1>
public class TransferStateConsumer extends Consumer {

  public Effect onUpdate(TransferState transferState) { // <2>
    // processing transfer state change
    return effects().done();
  }
}
// end::workflow-consumer[]
