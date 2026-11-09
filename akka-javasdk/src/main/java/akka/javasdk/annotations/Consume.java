/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.workflow.Workflow;
import java.lang.annotation.*;

/**
 * Annotation for providing ways to consume a stream of messages from Entities, other services, or
 * message broker topics.
 *
 * <p>Use on {@link akka.javasdk.consumer.Consumer} or {@link akka.javasdk.view.TableUpdater}.
 */
public @interface Consume {

  /**
   * Annotation for consuming state updates from a {@link KeyValueEntity}.
   *
   * <p>The underlying method must be declared to receive one parameter for the received state
   * changes.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface FromKeyValueEntity {
    /**
     * Assign the class type of the entity one intends to consume from, which must extend {@link
     * KeyValueEntity}.
     */
    Class<? extends KeyValueEntity<?>> value();
  }

  /**
   * Annotation for consuming events from an {@link EventSourcedEntity}.
   *
   * <p>The underlying method must be declared to receive one parameter for the received events. Use
   * one method with the common event type as parameter, or several methods with different parameter
   * types corresponding to different event types.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface FromEventSourcedEntity {
    /**
     * Assign the class type of the entity one intends to consume from, which must extend {@link
     * EventSourcedEntity EventSourcedEntity}.
     */
    Class<? extends EventSourcedEntity<?, ?>> value();

    /**
     * This option is only available for classes. Using it in a method has no effect.
     *
     * <p>When there is no method in the class whose input type matches the event type:
     *
     * <ul>
     *   <li>if ignoreUnknown is true the event is discarded
     *   <li>if false, an Exception is raised
     * </ul>
     *
     * <p>Be aware that true also turns off the build-time check that every event type has a
     * matching method. If you add an event type to the entity later, this class discards those
     * events without an error and never processes them.
     */
    boolean ignoreUnknown() default false;
  }

  /**
   * Annotation for consuming state updates from a {@link akka.javasdk.workflow.Workflow}.
   *
   * <p>The underlying method must be declared to receive one parameter for the received workflow
   * state changes.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface FromWorkflow {
    /**
     * Assign the class type of the workflow one intends to consume from, which must extend {@link
     * akka.javasdk.workflow.Workflow}.
     */
    Class<? extends Workflow<?>> value();
  }

  /**
   * Annotation for consuming messages from a topic (i.e PubSub or Kafka topic).
   *
   * <p>The underlying method must be declared to receive one parameter for the received messages.
   * Use one method with the common message type as parameter, or several methods with different
   * parameter types corresponding to different messages types.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface FromTopic {
    /** Assign the name of the topic to consume the stream from. */
    String value();

    /** Assign the consumer group name to be used on the broker. */
    String consumerGroup() default "";

    /**
     * This option is only available for classes. Using it in a method has no effect.
     *
     * <p>When there is no method in the class whose input type matches the event type:
     *
     * <ul>
     *   <li>if ignoreUnknown is true the event is discarded
     *   <li>if false, an Exception is raised
     * </ul>
     *
     * <p>Be aware that this class then also discards message types that the producer adds later,
     * before this class has a method for them, and never processes those messages.
     */
    boolean ignoreUnknown() default false;
  }

  /**
   * Annotation for consuming messages from another service.
   *
   * <p>The underlying method must be declared to receive one parameter for the received messages.
   * Use one method with the common event type as parameter, or several methods with different
   * parameter types corresponding to different message types.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface FromServiceStream {

    /** The unique identifier of the stream in the producing service */
    String id();

    /**
     * The deployed name of the service to consume from, can be the deployed name of another service
     * in the same project or a fully qualified public hostname of a service in a different project.
     * Together with {@link #platformService()}, it is the bare name of a service in that platform
     * service.
     *
     * <p>Note: The service name is used as unique identifier for tracking progress when consuming
     * it. Changing this name will lead to starting over from the beginning of the event stream.
     *
     * <p>Can be a template referencing an environment variable "${MY_ENV_NAME}" set for the service
     * at deployment.
     */
    String service();

    /**
     * The platform service the producing service belongs to, when consuming from a service that is
     * part of a platform service enabled in this project rather than a service in the project
     * itself. Empty when the producing service is a service in this project.
     *
     * <p>When set, {@link #service()} must be a bare service name. Neither value can be a template.
     *
     * <p>Note: The platform service is part of the identifier for tracking progress. Setting or
     * changing it starts over from the beginning of the event stream.
     *
     * <p>In dev mode, the producing service is looked up by name only. In the test kit, a mocked
     * stream is registered by service name and stream id, and also applies to a service with that
     * name in a platform service. A deployment outside the Akka platform fails at start with this
     * setting.
     */
    String platformService() default "";

    /**
     * In case you need to consume the same stream multiple times, each subscription should have a
     * unique consumer group.
     *
     * <p>Changing the consumer group will lead to starting over from the beginning of the stream.
     */
    String consumerGroup() default "";

    /**
     * When there is no method in the class whose input type matches the event type:
     *
     * <ul>
     *   <li>if ignoreUnknown is true the event is discarded
     *   <li>if false, an Exception is raised
     * </ul>
     *
     * <p>Be aware that this class then also discards message types that the producer adds later,
     * before this class has a method for them, and never processes those messages.
     */
    boolean ignoreUnknown() default false;
  }

  /**
   * Where a {@link akka.javasdk.consumer.Consumer} starts when it is deployed for the first time.
   *
   * <p>Without this annotation a new consumer processes the whole history of its source. With
   * {@code latest} it starts from the time of its first deployment, with {@code timestamp} it
   * starts from the given point in time. Exactly one of the two must be set. Events and state
   * changes from before the start point are not consumed.
   *
   * <p>Supported for consumers of Event Sourced Entities, Key Value Entities, Workflows and service
   * to service event streams. Not supported for consumers of message broker topics.
   *
   * <p>The start point is recorded on the first deployment and is permanent. The consumer never
   * receives events from before it, also not when its stored offsets are cleared. A consumer that
   * is already deployed and gets this annotation skips the events from before the start point that
   * it has not yet processed.
   *
   * <p>The service refuses to start if the start point is removed or changed to another timestamp.
   * Changing a timestamp to {@code latest} is accepted and keeps the recorded timestamp. Changing
   * {@code latest} to a timestamp is refused. To start over from a different point, give the
   * consumer a new component id. That also discards its processing progress. A consumer that is
   * added again with an old component id reuses its recorded start point and stored offsets. To
   * change a recorded start point, contact support.
   *
   * <p>In development mode and in tests nothing is recorded. {@code latest} is then the time of
   * each start of the service, and events that are written while the service is stopped are not
   * delivered. Messages that a test publishes through the test kit do not go through the start
   * point. A timestamp in the future delivers nothing until that time.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface StartFrom {

    /** Start from the time of the first deployment of the consumer. */
    boolean latest() default false;

    /** Start from a point in time, an ISO-8601 instant such as {@code "2026-10-01T00:00:00Z"}. */
    String timestamp() default "";
  }
}
