/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.workflow.Workflow;

import java.lang.annotation.*;

/**
 * Annotation for providing ways to consume a stream of messages from Entities, other services,
 * or message broker topics.
 * <p>
 * Use on {@link akka.javasdk.consumer.Consumer} or {@link akka.javasdk.view.TableUpdater}.
 */
public @interface Consume {

  /**
   * Annotation for consuming state updates from a {@link KeyValueEntity}.
   * <p>
   * The underlying method must be declared to receive one parameter for
   * the received state changes.
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
   * Annotation for consuming events from an {@link EventSourcedEntity}.
   * <p>
   * The underlying method must be declared to receive one parameter for
   * the received events. Use one method with the common event type as parameter,
   * or several methods with different parameter types corresponding to different
   * event types.
   *
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface FromEventSourcedEntity {
    /**
     * Assign the class type of the entity one intends to consume from, which must extend
     * {@link EventSourcedEntity EventSourcedEntity}.
     */
    Class<? extends EventSourcedEntity<?, ?>> value();

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
   * Annotation for consuming state updates from a {@link akka.javasdk.workflow.Workflow}.
   * <p>
   * The underlying method must be declared to receive one parameter for
   * the received workflow state changes.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface FromWorkflow {
    /**
     * Assign the class type of the workflow one intends to consume from, which must extend
     *  {@link akka.javasdk.workflow.Workflow}.
     */
    Class<? extends Workflow<?>> value();
  }

  /**
   * Annotation for consuming messages from a topic (i.e PubSub or Kafka topic).
   * <p>
   * The underlying method must be declared to receive one parameter for
   * the received messages. Use one method with the common message type as parameter,
   * or several methods with different parameter types corresponding to different
   * messages types.
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
   * Annotation for consuming messages from another service.
   * <p>
   * The underlying method must be declared to receive one parameter for
   * the received messages. Use one method with the common event type as parameter,
   * or several methods with different parameter types corresponding to different
   * message types.
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
     * service in the same project or a fully qualified public hostname of a service in a
     * different project.
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
