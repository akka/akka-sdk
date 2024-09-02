/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.*;

/** Annotation for aggregating ways of producing outgoing information. */
public @interface Produce {

  /**
   * Annotation for marking a method as producing information to be published on a PubSub or Kafka
   * topic.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface ToTopic {
    /** Assign the name of the topic to be used for eventing out. */
    String value();
  }


  /**
   * Annotation to configure the component to produce an event stream that can be consumed by other services.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface ServiceStream {
    /**
     * Identifier for the event stream. Must be unique inside the same service.
     */
    String id();
  }
}
