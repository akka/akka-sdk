/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.Metadata;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import akka.javasdk.impl.client.MethodRefResolver;
import akka.javasdk.impl.reflection.Reflect;
import akka.javasdk.testkit.impl.EventSourcedEntityEffectsRunner;
import akka.javasdk.testkit.impl.TestKitEventSourcedEntityContext;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static akka.javasdk.testkit.EntitySerializationChecker.verifySerDerWithExpectedType;

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
   * derive a state for the generated entity.
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

  public final class MethodRef<R> {
    private final akka.japi.function.Function<ES, EventSourcedEntity.Effect<R>> func;
    private final Metadata metadata;

    public MethodRef(akka.japi.function.Function<ES, EventSourcedEntity.Effect<R>> func, Metadata metadata) {
      this.func = func;
      this.metadata = metadata;
    }

    public MethodRef<R> withMetadata(Metadata metadata) {
      return new MethodRef<>(func, metadata);
    }

    public EventSourcedResult<R> invoke() {
      var method = MethodRefResolver.resolveMethodRef(func);
      var returnType = Reflect.getReturnType(entity.getClass(), method);
      return EventSourcedTestKit.this.call(func, metadata, Optional.of(returnType));
    }
  }

  public final class MethodRef1<I, R> {
    private final akka.japi.function.Function2<ES, I, EventSourcedEntity.Effect<R>> func;
    private final Metadata metadata;

    public MethodRef1(akka.japi.function.Function2<ES, I, EventSourcedEntity.Effect<R>> func, Metadata metadata) {
      this.func = func;
      this.metadata = metadata;
    }

    public MethodRef1<I, R> withMetadata(Metadata metadata) {
      return new MethodRef1<>(func, metadata);
    }

    public EventSourcedResult<R> invoke(I input) {
      var method = MethodRefResolver.resolveMethodRef(func);
      var returnType = Reflect.getReturnType(entity.getClass(), method);
      var inputType = method.getParameterTypes()[0];

      verifySerDerWithExpectedType(inputType, input, entity);

      return EventSourcedTestKit.this.call(es -> {
        try {
          return func.apply(es, input);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }, metadata, Optional.of(returnType));
    }
  }

  /**
   * Pass in an Event Sourced Entity command handler method reference without parameters, e.g. {@code UserEntity::create}
   */
  public <R> MethodRef<R> method(akka.japi.function.Function<ES, EventSourcedEntity.Effect<R>> func) {
    return new MethodRef<>(func, Metadata.EMPTY);
  }

  /**
   * Pass in an Event Sourced Entity command handler method reference with a single parameter, e.g. {@code UserEntity::create}
   */
  public <I, R> MethodRef1<I, R> method(akka.japi.function.Function2<ES, I, EventSourcedEntity.Effect<R>> func) {
    return new MethodRef1<>(func, Metadata.EMPTY);
  }

  /**
   * The call method can be used to simulate a call to the EventSourcedEntity. The passed java
   * lambda should return an EventSourcedEntity.Effect. The Effect is interpreted into an
   * EventSourcedResult that can be used in test assertions.
   *
   * @param func A function from EventSourcedEntity to EventSourcedEntity.Effect.
   * @return a EventSourcedResult
   * @param <R> The type of reply that is expected from invoking a command handler
   * @deprecated Use "method(MyEntity::myCommandHandler).invoke()" instead
   */
  @Deprecated(since = "3.2.1", forRemoval = true)
  public <R> EventSourcedResult<R> call(akka.japi.function.Function<ES, EventSourcedEntity.Effect<R>> func) {
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
   * @deprecated Use "method(MyEntity::myCommandHandler).withMetadata(metadata).invoke()" instead
   */
  @SuppressWarnings("unchecked") // entity() returns the entity we were constructed with
  @Deprecated(since = "3.2.1", forRemoval = true)
  public <R> EventSourcedResult<R> call(akka.japi.function.Function<ES, EventSourcedEntity.Effect<R>> func, Metadata metadata) {
    return call(func, metadata, Optional.empty());
  }

  private <R> EventSourcedResult<R> call(akka.japi.function.Function<ES, EventSourcedEntity.Effect<R>> func, Metadata metadata, Optional<Class<?>> returnType) {
    return interpretEffects(() -> {
      try {
        return func.apply((ES) entity());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }, entityId, metadata,returnType);
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
