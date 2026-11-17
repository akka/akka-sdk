/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracing;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

@Component(id = "span-recording-consumer")
@Consume.FromEventSourcedEntity(SpanRecordingEntity.class)
public class SpanRecordingConsumer extends Consumer {

  public Effect onEvent(SpanRecordingEntity.Event event) {
    SpanRecorder.record("consumer:" + messageContext().eventSubject().get());
    return effects().done();
  }
}
