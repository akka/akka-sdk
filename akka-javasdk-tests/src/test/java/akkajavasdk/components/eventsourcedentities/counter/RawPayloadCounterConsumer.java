/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.counter;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * Copyright (C) 2009-2025 Lightbend Inc. <https://www.lightbend.com>
 */
@Component(id = "raw-counter-consumer")
@Consume.FromEventSourcedEntity(value = CounterEntity.class)
public class RawPayloadCounterConsumer extends Consumer {

  public static final List<String> seenEvents = Collections.synchronizedList(new ArrayList<>());

  // specific types take precedence
  public Effect increased(CounterEvent.ValueIncreased event) {
    seenEvents.add(event.toString());
    return effects().done();
  }

  // all payloads with no specific handler
  // Note: this doesn't really make sense for an event sourced entity, but is easier to test than
  // for a service to service
  //       or topic which are sensible use cases for raw payloads
  public Effect raw(byte[] bytes) {
    seenEvents.add(
        "raw-bytes: " + messageContext().metadata().asCloudEvent().datacontenttype().get());
    return effects().done();
  }
}
