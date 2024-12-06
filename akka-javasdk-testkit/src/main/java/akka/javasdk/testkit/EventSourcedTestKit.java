/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.Metadata;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import akka.javasdk.testkit.impl.EventSourcedEntityEffectsRunner;
import akka.javasdk.testkit.impl.TestKitEventSourcedEntityContext;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * EventSourced Testkit for use in unit tests for EventSourced entities.
 *
 * <p>To test a EventSourced create a testkit instance by calling one of the available {@code
 * EventSourcedTestKit.of} methods. The returned testkit is stateful, and it holds internally the
 * state of the entity.
 *
 * <p>Use the {@code call} methods to interact with the testkit.
 */
public class EventSourcedTestKit<S, E, ES extends EventSourcedEntity<S, E>>
    extends EventSourcedEntityEffectsRunner<S, E> {

  private final ES entity;
  private final String entityId;

  private EventSourcedTestKit(ES entity, String entityId) {
    super(entity);
    this.entity = entity;
    this.entityId = entityId;
  }

  /**
   * Creates a new testkit instance from a EventSourcedEntity Supplier.
   *
   * <p>A default test entity id will be automatically provided.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> of(
      Supplier<ES> entityFactory) {
    return of("testkit-entity-id", entityFactory);
  }

  /**
   * Creates a new testkit instance from a function EventSourcedEntityContext to EventSourcedEntity.
   *
   * <p>A default test entity id will be automatically provided.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> of(
      Function<EventSourcedEntityContext, ES> entityFactory) {
    return of("testkit-entity-id", entityFactory);
  }

  /**
   * Creates a new testkit instance from a user defined entity id and an EventSourcedEntity
   * Supplier.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> of(
      String entityId, Supplier<ES> entityFactory) {
    return of(entityId, ctx -> entityFactory.get());
  }

  /**
   * Creates a new testkit instance from a user defined entity id and a function
   * EventSourcedEntityContext to EventSourcedEntity.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> of(
      String entityId, Function<EventSourcedEntityContext, ES> entityFactory) {
    EventSourcedEntityContext context = new TestKitEventSourcedEntityContext(entityId);
    return new EventSourcedTestKit<>(entityFactory.apply(context), entityId);
  }

  /**
   * The call method can be used to simulate a call to the EventSourcedEntity. The passed java
   * lambda should return an EventSourcedEntity.Effect. The Effect is interpreted into an
   * EventSourcedResult that can be used in test assertions.
   *
   * @param func A function from EventSourcedEntity to EventSourcedEntity.Effect.
   * @return a EventSourcedResult
   * @param <R> The type of reply that is expected from invoking a command handler
   */
  public <R> EventSourcedResult<R> call(Function<ES, EventSourcedEntity.Effect<R>> func) {
    return call(func, Metadata.EMPTY);
  }

  /**
   * The call method can be used to simulate a call to the EventSourcedEntity. The passed java
   * lambda should return an EventSourcedEntity.Effect. The Effect is interpreted into an
   * EventSourcedResult that can be used in test assertions.
   *
   * @param func     A function from EventSourcedEntity to EventSourcedEntity.Effect.
   * @param metadata A metadata passed as a call context.
   * @param <R>      The type of reply that is expected from invoking a command handler
   * @return a EventSourcedResult
   */
  public <R> EventSourcedResult<R> call(Function<ES, EventSourcedEntity.Effect<R>> func, Metadata metadata) {
    return interpretEffects(() -> func.apply(entity), entityId, metadata);
  }

  @Override
  protected final S handleEvent(S state, E event) {
    return entity.applyEvent(event);
  }
}
