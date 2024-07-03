/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.annotations;

import java.lang.annotation.*;

/**
 * Annotation for providing ways to consume to different streams of information: value entities,
 * event-sourced entities or topic.
 */
public @interface Consume {

  /**
   * Annotation for consuming state updates from a Value Entity. It can be used both at type and
   * method levels. When used at type level, it means the `View` or `Action` will not be transforming state.
   * When used at method level, it gives the ability to transform the updates into a different state.
   */
  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface FromValueEntity {
    /**
     * Assign the class type of the entity one intends to consume from, which must extend
     *  {@link kalix.javasdk.valueentity.ValueEntity ValueEntity}.
     */
    Class<? extends kalix.javasdk.valueentity.ValueEntity<?>> value();

    /**
     * When true at type level of the `View`, it will automatically delete the view state based on ValueEntity deletion fact.
     * When true at method level it allows to create a special handler for deletes (must be declared to receive zero parameters):
     * <pre>{@code
     * @Consume.FromValueEntity(MyValueEntity.class)
     * public UpdateEffect<MyView> onChange(ValueEntity valueEntity) {
     *   return effects().updateState(...);
     * }
     *
     * @Consume.FromValueEntity(value = MyValueEntity.class, handleDeletes = true)
     * public UpdateEffect<MyView> onDelete() {
     *   return effects().deleteState();
     * }
     * </pre>
     *
     * The flag has no effect when used at type level of the `Action`. On the `Action` method level it allows to create
     * a delete handler, similar to the example above.
     *
     */
    boolean handleDeletes() default false;
  }

  /**
   * Annotation for consuming events from an Event-sourced Entity.
   *
   * The underlying method must be declared to receive one parameter.
   *
   */
  @Target({ElementType.METHOD, ElementType.TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @interface FromEventSourcedEntity {
    /**
     * Assign the class type of the entity one intends to consume from, which must extend
     * {@link kalix.javasdk.eventsourcedentity.EventSourcedEntity EventSourcedEntity}.
     */
    Class<? extends kalix.javasdk.eventsourcedentity.EventSourcedEntity<?, ?>> value();

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
  @Target({ElementType.METHOD, ElementType.TYPE})
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