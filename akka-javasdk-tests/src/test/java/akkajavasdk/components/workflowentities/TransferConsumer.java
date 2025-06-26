/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.consumer.Consumer;

@ComponentId("transfer-consumer")
@Consume.FromWorkflow(TransferWorkflow.class)
public class TransferConsumer extends Consumer {

  public static final String TRANSFER_CONSUMER_STORE = "transfer-consumer-store";

  public Effect onTransfer(TransferState transfer) {
    DummyTransferStore.store(TRANSFER_CONSUMER_STORE, messageContext().eventSubject().get(), transfer);
    return effects().done();
  }

  @DeleteHandler
  public Effect onDelete() {
    DummyTransferStore.delete(TRANSFER_CONSUMER_STORE, messageContext().eventSubject().get());
    return effects().done();
  }
}
