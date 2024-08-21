/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.annotations;

import akka.platform.javasdk.keyvalueentity.KeyValueEntity;

import java.lang.annotation.*;

/**
 * Annotation for providing ways to consume to different streams of information: value entities,
 * event-sourced entities or topic.
 */
public @interface Consume {

  /**
   * Annotation for consuming state updates from a Key Value Entity.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface FromKeyValueEntity {
    /**
     * Assign the class type of the entity one intends to consume from, which must extend
     *  {@link KeyValueEntity}.
     */
    Class<? extends KeyValueEntity<?>> value();
  }

  /**
   * Annotation for consuming events from an Event-sourced Entity.
   *
   * The underlying method must be declared to receive one parameter.
   *
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface FromEventSourcedEntity {
    /**
     * Assign the class type of the entity one intends to consume from, which must extend
     * {@link akka.platform.javasdk.eventsourcedentity.EventSourcedEntity EventSourcedEntity}.
     */
    Class<? extends akka.platform.javasdk.eventsourcedentity.EventSourcedEntity<?, ?>> value();

    /**
     * This option is only available for classes. Using it in a method has no effect.
     *
     * <p>
     * When there is no method in the class whose input type matches the event type:
     * <ul>
     *   <li>if ignoreUnknown is true the event is discarded</li>
     *   <li>if false, an Exception is raised</li>
     * </ul>
     */
    boolean ignoreUnknown() default false;
  }

  /**
   * Annotation for consuming messages from a topic (i.e PubSub or Kafka topic).
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface FromTopic {
    /**
     * Assign the name of the topic to consume the stream from.
     */
    String value();

    /**
     * Assign the consumer group name to be used on the broker.
     */
    String consumerGroup() default "";

    /**
     * This option is only available for classes. Using it in a method has no effect.
     *
     * <p>
     * When there is no method in the class whose input type matches the event type:
     * <ul>
     *   <li>if ignoreUnknown is true the event is discarded</li>
     *   <li>if false, an Exception is raised</li>
     * </ul>
     **/
    boolean ignoreUnknown() default false;
  }


  /**
   * Annotation for consuming messages from another Kalix service.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface FromServiceStream {

    /**
     * The unique identifier of the stream in the producing service
     */
    String id();

    /**
     * The deployed name of the service to consume from, can be the deployed name of another
     * Kalix service in the same Kalix Project or a fully qualified public hostname of
     * a Kalix service in a different project.
     * <p>
     * Note: The service name is used as unique identifier for tracking progress when consuming it.
     * Changing this name will lead to starting over from the beginning of the event stream.
     * <p>
     * Can be a template referencing an environment variable "${MY_ENV_NAME}" set for the service at deployment.
     */
    String service();

    /**
     * In case you need to consume the same stream multiple times, each subscription should have a unique consumer group.
     * <p>
     * Changing the consumer group will lead to starting over from the beginning of the stream.
     */
    String consumerGroup() default "";

    /**
     * When there is no method in the class whose input type matches the event type:
     * <ul>
     *   <li>if ignoreUnknown is true the event is discarded</li>
     *   <li>if false, an Exception is raised</li>
     * </ul>
     **/
    boolean ignoreUnknown() default false;
  }
}
