/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.ledger;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.ledger.ClassificationRecord;
import akka.javasdk.ledger.FlowRecord;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A ledger consumer that selects classifications only and stores each record it receives. */
@Component(id = "classification-record-consumer")
@Consume.FromLedger
public class ClassificationRecordConsumer extends Consumer {

  public static final List<FlowRecord> receivedRecords = new CopyOnWriteArrayList<>();

  public Effect onClassification(ClassificationRecord record) {
    receivedRecords.add(record);
    return effects().done();
  }
}
