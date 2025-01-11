/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.Metadata;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import akka.javasdk.testkit.impl.EventSourcedEntityEffectsRunner;
import akka.javasdk.testkit.impl.TestKitEventSourcedEntityContext;

import java.util.List;
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

  private final String entityId;

  public static final String DEFAULT_TEST_ENTITY_ID = "testkit-entity-id";

  private EventSourcedTestKit(ES entity, String entityId) {
    super(entity);
    this.entityId = entityId;
  }

  private EventSourcedTestKit(ES entity, String entityId, S initialState) {
    super(entity, initialState);
    this.entityId = entityId;
  }

  private EventSourcedTestKit(ES entity, String entityId, List<E> initialEvents) {
    super(entity, initialEvents);
    this.entityId = entityId;
  }

  /**
   * Creates a new testkit instance from a EventSourcedEntity Supplier.
   *
   * <p>A default test entity id will be automatically provided.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> of(
      Supplier<ES> entityFactory) {
    return of(DEFAULT_TEST_ENTITY_ID, entityFactory);
  }

  /**
   * Creates a new testkit instance from a Supplier of EventSourcedEntity and a state.
   *
   * <p>A default test entity id will be automatically provided.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> ofEntityWithState(
      Supplier<ES> entityFactory, S initialState) {
    return ofEntityWithState(DEFAULT_TEST_ENTITY_ID, entityFactory, initialState);
  }

  /**
   * Creates a new testkit instance from a Supplier of EventSourcedEntity and events from which to
   * derive a state for the generated entity for tests.
   *
   * <p>A default test entity id will be automatically provided.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> ofEntityFromEvents(
      Supplier<ES> entityFactory, List<E> initialEvents) {
    return ofEntityFromEvents(DEFAULT_TEST_ENTITY_ID, entityFactory, initialEvents);
  }

  /**
   * Creates a new testkit instance from a function EventSourcedEntityContext to EventSourcedEntity.
   *
   * <p>A default test entity id will be automatically provided.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> of(
      Function<EventSourcedEntityContext, ES> entityFactory) {
    return of(DEFAULT_TEST_ENTITY_ID, entityFactory);
  }

  /**
   * Creates a new testkit instance from a factory function for EventSourcedEntity and a state into
   * which the built entity will be placed for tests.
   *
   * <p>A default test entity id will be automatically provided.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> ofEntityWithState(
      Function<EventSourcedEntityContext, ES> entityFactory, S initialState) {
    return ofEntityWithState(DEFAULT_TEST_ENTITY_ID, entityFactory, initialState);
  }

  /**
   * Creates a new testkit instance from a factory function for EventSourcedEntity and events from
   * which to derive a state for the generated entity for tests.
   *
   * <p>A default test entity id will be automatically provided.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> ofEntityFromEvents(
      Function<EventSourcedEntityContext, ES> entityFactory, List<E> initialEvents) {
    return ofEntityFromEvents(DEFAULT_TEST_ENTITY_ID, entityFactory, initialEvents);
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
   * Creates a new testkit instance from a user defined entity id, a Supplier of EventSourcedEntity,
   * and a state into which the supplied entity will be placed for tests.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> ofEntityWithState(
      String entityId, Supplier<ES> entityFactory, S initialState) {
    return ofEntityWithState(entityId, ctx -> entityFactory.get(), initialState);
  }

  /**
   * Creates a new testkit instance from a user defined entity id, a Supplier of EventSourcedEntity,
   * and events from which to derive a state for the generated entity for tests.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> ofEntityFromEvents(
      String entityId, Supplier<ES> entityFactory, List<E> initialEvents) {
    return ofEntityFromEvents(entityId, ctx -> entityFactory.get(), initialEvents);
  }

  /**
   * Creates a new testkit instance from a user defined entity id and a function
   * EventSourcedEntityContext to EventSourcedEntity.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> of(
      String entityId, Function<EventSourcedEntityContext, ES> entityFactory) {
    return new EventSourcedTestKit<>(entityWithId(entityId, entityFactory), entityId);
  }

  /**
   * Creates a new testkit instance from a user defined entity id, a factory function for
   * EventSourcedEntity, and a state into which the built entity will be placed for tests.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> ofEntityWithState(
      String entityId, Function<EventSourcedEntityContext, ES> entityFactory, S initialState) {
    return new EventSourcedTestKit<>(entityWithId(entityId, entityFactory), entityId, initialState);
  }

  /**
   * Creates a new testkit instance from a user defined entity id, a factory function for
   * EventSourcedEntity, and events from which to derive a state for the generated entity for tests.
   */
  public static <S, E, ES extends EventSourcedEntity<S, E>> EventSourcedTestKit<S, E, ES> ofEntityFromEvents(
      String entityId, Function<EventSourcedEntityContext, ES> entityFactory, List<E> initialEvents) {
    return new EventSourcedTestKit<>(entityWithId(entityId, entityFactory), entityId, initialEvents);
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
  @SuppressWarnings("unchecked") // entity() returns the entity we were constructed with
  public <R> EventSourcedResult<R> call(Function<ES, EventSourcedEntity.Effect<R>> func, Metadata metadata) {
    return interpretEffects(() -> func.apply((ES)entity()), entityId, metadata);
  }

  @Override
  protected final S handleEvent(S state, E event) {
    return entity().applyEvent(event);
  }

  private static <S, E, ES extends EventSourcedEntity<S, E>> ES entityWithId(
      String entityId, Function<EventSourcedEntityContext, ES> entityFactory) {
    EventSourcedEntityContext context = new TestKitEventSourcedEntityContext(entityId);
    return entityFactory.apply(context);
  }
}
