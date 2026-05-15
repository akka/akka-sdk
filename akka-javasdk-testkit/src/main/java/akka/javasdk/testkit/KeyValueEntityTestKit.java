/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static akka.javasdk.testkit.EntitySerializationChecker.verifySerDerWithExpectedType;

import akka.javasdk.Metadata;
import akka.javasdk.impl.client.MethodRefResolver;
import akka.javasdk.impl.reflection.Reflect;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import akka.javasdk.testkit.impl.KeyValueEntityResultImpl;
import akka.javasdk.testkit.impl.TestKitKeyValueEntityCommandContext;
import akka.javasdk.testkit.impl.TestKitKeyValueEntityContext;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * KeyValueEntity Testkit for use in unit tests for Value entities.
 *
 * <p>To test a KeyValueEntity create a testkit instance by calling one of the available {@code
 * KeyValueEntityTestKit.of} methods. The returned testkit is stateful, and it holds internally the
 * state of the entity.
 *
 * <p>Use the {@code method} methods to interact with the testkit.
 */
public class KeyValueEntityTestKit<S, E extends KeyValueEntity<S>> {

  public static final String DEFAULT_TEST_ENTITY_ID = "testkit-entity-id";

  private final Class<?> stateClass;
  private S state;
  private boolean deleted;
  private final S emptyState;
  private final E entity;
  private final String entityId;

  private KeyValueEntityTestKit(String entityId, E entity) {
    this.entityId = entityId;
    this.entity = entity;
    this.state = entity.emptyState();
    this.stateClass = Reflect.keyValueEntityStateType(entity.getClass());
    this.emptyState = state;
    this.deleted = false;
  }

  private KeyValueEntityTestKit(String entityId, E entity, S initialState) {
    this.entityId = entityId;
    this.entity = entity;
    this.stateClass = Reflect.keyValueEntityStateType(entity.getClass());
    this.emptyState = entity.emptyState();
    verifySerDerWithExpectedType(stateClass, initialState, entity);
    this.state = initialState;
    this.deleted = false;
  }

  /**
   * Creates a new testkit instance from a KeyValueEntity Supplier.
   *
   * <p>A default test entity id will be automatically provided.
   */
  public static <S, E extends KeyValueEntity<S>> KeyValueEntityTestKit<S, E> of(
      Supplier<E> entityFactory) {
    return of(ctx -> entityFactory.get());
  }

  /**
   * Creates a new testkit instance from a function KeyValueEntityContext to KeyValueEntity.
   *
   * <p>A default test entity id will be automatically provided.
   */
  public static <S, E extends KeyValueEntity<S>> KeyValueEntityTestKit<S, E> of(
      Function<KeyValueEntityContext, E> entityFactory) {
    return of(DEFAULT_TEST_ENTITY_ID, entityFactory);
  }

  /** Creates a new testkit instance from a user defined entity id and a KeyValueEntity Supplier. */
  public static <S, E extends KeyValueEntity<S>> KeyValueEntityTestKit<S, E> of(
      String entityId, Supplier<E> entityFactory) {
    return of(entityId, ctx -> entityFactory.get());
  }

  /**
   * Creates a new testkit instance from a user defined entity id and a function
   * KeyValueEntityContext to KeyValueEntity.
   */
  public static <S, E extends KeyValueEntity<S>> KeyValueEntityTestKit<S, E> of(
      String entityId, Function<KeyValueEntityContext, E> entityFactory) {
    TestKitKeyValueEntityContext context = new TestKitKeyValueEntityContext(entityId);
    return new KeyValueEntityTestKit<>(entityId, entityFactory.apply(context));
  }

  /**
   * Creates a new testkit instance from a KeyValueEntity Supplier, starting with the given initial
   * state instead of the entity's {@code emptyState()}. Subsequent {@code deleteEntity()} effects
   * still reset the state to the entity's {@code emptyState()}.
   *
   * <p>A default test entity id will be automatically provided.
   */
  public static <S, E extends KeyValueEntity<S>> KeyValueEntityTestKit<S, E> ofEntityWithState(
      Supplier<E> entityFactory, S initialState) {
    return ofEntityWithState(ctx -> entityFactory.get(), initialState);
  }

  /**
   * Creates a new testkit instance from a function KeyValueEntityContext to KeyValueEntity,
   * starting with the given initial state instead of the entity's {@code emptyState()}. Subsequent
   * {@code deleteEntity()} effects still reset the state to the entity's {@code emptyState()}.
   *
   * <p>A default test entity id will be automatically provided.
   */
  public static <S, E extends KeyValueEntity<S>> KeyValueEntityTestKit<S, E> ofEntityWithState(
      Function<KeyValueEntityContext, E> entityFactory, S initialState) {
    return ofEntityWithState(DEFAULT_TEST_ENTITY_ID, entityFactory, initialState);
  }

  /**
   * Creates a new testkit instance from a user defined entity id and a KeyValueEntity Supplier,
   * starting with the given initial state instead of the entity's {@code emptyState()}. Subsequent
   * {@code deleteEntity()} effects still reset the state to the entity's {@code emptyState()}.
   */
  public static <S, E extends KeyValueEntity<S>> KeyValueEntityTestKit<S, E> ofEntityWithState(
      String entityId, Supplier<E> entityFactory, S initialState) {
    return ofEntityWithState(entityId, ctx -> entityFactory.get(), initialState);
  }

  /**
   * Creates a new testkit instance from a user defined entity id and a function
   * KeyValueEntityContext to KeyValueEntity, starting with the given initial state instead of the
   * entity's {@code emptyState()}. Subsequent {@code deleteEntity()} effects still reset the state
   * to the entity's {@code emptyState()}.
   */
  public static <S, E extends KeyValueEntity<S>> KeyValueEntityTestKit<S, E> ofEntityWithState(
      String entityId, Function<KeyValueEntityContext, E> entityFactory, S initialState) {
    TestKitKeyValueEntityContext context = new TestKitKeyValueEntityContext(entityId);
    return new KeyValueEntityTestKit<>(entityId, entityFactory.apply(context), initialState);
  }

  /**
   * @return The current state of the key value entity under test
   */
  public S getState() {
    return state;
  }

  /**
   * @return true if the entity is deleted
   */
  public boolean isDeleted() {
    return deleted;
  }

  public final class MethodRef<R> {
    private final akka.japi.function.Function<E, KeyValueEntity.Effect<R>> func;
    private final Metadata metadata;

    public MethodRef(
        akka.japi.function.Function<E, KeyValueEntity.Effect<R>> func, Metadata metadata) {
      this.func = func;
      this.metadata = metadata;
    }

    public MethodRef<R> withMetadata(Metadata metadata) {
      return new MethodRef<>(func, metadata);
    }

    public KeyValueEntityResult<R> invoke() {
      var method = MethodRefResolver.resolveMethodRef(func);
      var returnType = Reflect.getReturnType(entity.getClass(), method);
      return KeyValueEntityTestKit.this.call(func, metadata, Optional.of(returnType));
    }
  }

  public final class MethodRef1<I, R> {
    private final akka.japi.function.Function2<E, I, KeyValueEntity.Effect<R>> func;
    private final Metadata metadata;

    public MethodRef1(
        akka.japi.function.Function2<E, I, KeyValueEntity.Effect<R>> func, Metadata metadata) {
      this.func = func;
      this.metadata = metadata;
    }

    public MethodRef1<I, R> withMetadata(Metadata metadata) {
      return new MethodRef1<>(func, metadata);
    }

    public KeyValueEntityResult<R> invoke(I input) {
      var method = MethodRefResolver.resolveMethodRef(func);
      var returnType = Reflect.getReturnType(entity.getClass(), method);
      var inputType = method.getParameterTypes()[0];

      verifySerDerWithExpectedType(inputType, input, entity);

      return KeyValueEntityTestKit.this.call(
          kve -> {
            try {
              return func.apply(kve, input);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          },
          metadata,
          Optional.of(returnType));
    }
  }

  /**
   * Pass in a Key Value Entity command handler method reference without parameters, e.g. {@code
   * UserEntity::create}
   */
  public <R> MethodRef<R> method(akka.japi.function.Function<E, KeyValueEntity.Effect<R>> func) {
    return new MethodRef<>(func, Metadata.EMPTY);
  }

  /**
   * Pass in a Key Value Entity command handler method reference with a single parameter, e.g.
   * {@code UserEntity::create}
   */
  public <I, R> MethodRef1<I, R> method(
      akka.japi.function.Function2<E, I, KeyValueEntity.Effect<R>> func) {
    return new MethodRef1<>(func, Metadata.EMPTY);
  }

  @SuppressWarnings("unchecked")
  private <Reply> KeyValueEntityResult<Reply> interpretEffects(
      Supplier<KeyValueEntity.Effect<Reply>> effect, Optional<Type> returnType) {
    KeyValueEntityResultImpl<Reply> result = new KeyValueEntityResultImpl<>(effect.get());
    if (result.stateWasUpdated()) {
      S state = (S) result.getUpdatedState();
      if (state == null) {
        throw new IllegalStateException("Updated state must not be null.");
      }
      this.state = state;
      verifySerDerWithExpectedType(stateClass, this.state, entity);
    } else if (result.stateWasDeleted()) {
      this.state = emptyState;
      this.deleted = true;
    }
    if (result.isReply()) {
      returnType.ifPresent(rt -> verifySerDerWithExpectedType(rt, result.getReply(), entity));
    }
    return result;
  }

  private <R> KeyValueEntityResult<R> call(
      akka.japi.function.Function<E, KeyValueEntity.Effect<R>> func,
      Metadata metadata,
      Optional<Type> returnType) {
    TestKitKeyValueEntityCommandContext commandContext =
        new TestKitKeyValueEntityCommandContext(entityId, metadata);
    entity._internalSetCommandContext(Optional.of(commandContext));
    entity._internalSetCurrentState(this.state, this.deleted);
    return interpretEffects(
        () -> {
          try {
            return func.apply(entity);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        },
        returnType);
  }
}
