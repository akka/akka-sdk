/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.streamproducer;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;

@Component(id = "stream-producer-sequence-events")
@Consume.FromEventSourcedEntity(SequenceEntity.class)
@Produce.ServiceStream(id = SequenceEvents.STREAM_ID)
@Acl(allow = @Acl.Matcher(service = "*"))
public class SequenceEvents extends Consumer {

  public static final String STREAM_ID = "sequence_events";

  public record PublicNumber(String entityId, int number) {}

  public Effect onEvent(SequenceEntity.Event event) {
    return switch (event) {
      case SequenceEntity.NumberAdded added ->
          effects()
              .produce(
                  new PublicNumber(messageContext().eventSubject().orElseThrow(), added.number()));
    };
  }
}
