/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.Metadata;
import akka.javasdk.impl.client.MethodRefResolver;
import akka.javasdk.impl.reflection.Reflect;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import akka.javasdk.testkit.impl.KeyValueEntityResultImpl;
import akka.javasdk.testkit.impl.TestKitKeyValueEntityCommandContext;
import akka.javasdk.testkit.impl.TestKitKeyValueEntityContext;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static akka.javasdk.testkit.EntitySerializationChecker.verifySerDerWithExpectedType;

/**
 * KeyValueEntity Testkit for use in unit tests for Value entities.
 *
 * <p>To test a KeyValueEntity create a testkit instance by calling one of the available
 * {@code KeyValueEntityTestKit.of} methods. The returned testkit is stateful, and it holds internally the
 * state of the entity.
 *
 * <p>Use the {@code call} methods to interact with the testkit.
 */
public class KeyValueEntityTestKit<S, E extends KeyValueEntity<S>> {

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
    return of("testkit-entity-id", entityFactory);
  }

  /**
   * Creates a new testkit instance from a user defined entity id and a KeyValueEntity Supplier.
   */
  public static <S, E extends KeyValueEntity<S>> KeyValueEntityTestKit<S, E> of(
    String entityId, Supplier<E> entityFactory) {
    return of(entityId, ctx -> entityFactory.get());
  }

  /**
   * Creates a new testkit instance from a user defined entity id and a function KeyValueEntityContext
   * to KeyValueEntity.
   */
  public static <S, E extends KeyValueEntity<S>> KeyValueEntityTestKit<S, E> of(
    String entityId, Function<KeyValueEntityContext, E> entityFactory) {
    TestKitKeyValueEntityContext context = new TestKitKeyValueEntityContext(entityId);
    return new KeyValueEntityTestKit<>(entityId, entityFactory.apply(context));
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

    public MethodRef(akka.japi.function.Function<E, KeyValueEntity.Effect<R>> func, Metadata metadata) {
      this.func = func;
      this.metadata = metadata;
    }

    public MethodRef<R> withMetadata(Metadata metadata) {
      return new MethodRef<>(func, metadata);
    }

    public KeyValueEntityResult<R> invoke() {
      var method = MethodRefResolver.resolveMethodRef(func);
      var returnType = Reflect.getReturnClass(entity.getClass(), method);
      return KeyValueEntityTestKit.this.call(func, metadata, Optional.of(returnType));
    }
  }

  public final class MethodRef1<I, R> {
    private final akka.japi.function.Function2<E, I, KeyValueEntity.Effect<R>> func;
    private final Metadata metadata;

    public MethodRef1(akka.japi.function.Function2<E, I, KeyValueEntity.Effect<R>> func, Metadata metadata) {
      this.func = func;
      this.metadata = metadata;
    }

    public MethodRef1<I, R> withMetadata(Metadata metadata) {
      return new MethodRef1<>(func, metadata);
    }

    public KeyValueEntityResult<R> invoke(I input) {
      var method = MethodRefResolver.resolveMethodRef(func);
      var returnType = Reflect.getReturnClass(entity.getClass(), method);
      var inputType = method.getParameterTypes()[0];

      verifySerDerWithExpectedType(inputType, input, entity);

      return KeyValueEntityTestKit.this.call(kve -> {
        try {
          return func.apply(kve, input);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }, metadata, Optional.of(returnType));
    }
  }

  /**
   * Pass in a Key Value Entity command handler method reference without parameters, e.g. {@code UserEntity::create}
   */
  public <R> MethodRef<R> method(akka.japi.function.Function<E, KeyValueEntity.Effect<R>> func) {
    return new MethodRef<>(func, Metadata.EMPTY);
  }

  /**
   * Pass in a Key Value Entity command handler method reference with a single parameter, e.g. {@code UserEntity::create}
   */
  public <I, R> MethodRef1<I, R> method(akka.japi.function.Function2<E, I, KeyValueEntity.Effect<R>> func) {
    return new MethodRef1<>(func, Metadata.EMPTY);
  }

  @SuppressWarnings("unchecked")
  private <Reply> KeyValueEntityResult<Reply> interpretEffects(Supplier<KeyValueEntity.Effect<Reply>> effect, Optional<Class<?>> returnType) {
    KeyValueEntityResultImpl<Reply> result = new KeyValueEntityResultImpl<>(effect.get());
    if (result.stateWasUpdated()) {
      this.state = (S) result.getUpdatedState();
      verifySerDerWithExpectedType(stateClass, state, entity);
    } else if (result.stateWasDeleted()) {
      this.state = emptyState;
      this.deleted = true;
    }
    if (result.isReply()) {
      returnType.ifPresent(rt -> verifySerDerWithExpectedType(rt, result.getReply(), entity));
    }
    return result;
  }

  /**
   * The call method can be used to simulate a call to the KeyValueEntity. The passed java lambda
   * should return a KeyValueEntity.Effect. The Effect is interpreted into a KeyValueEntityResult that can
   * be used in test assertions.
   *
   * @param func A function from KeyValueEntity to KeyValueEntity.Effect.
   * @param <R>  The type of reply that is expected from invoking a command handler
   * @return a KeyValueEntityResult
   * @deprecated Use "method(MyEntity::myCommandHandler).invoke()" instead
   */
  @Deprecated(since = "3.2.1", forRemoval = true)
  public <R> KeyValueEntityResult<R> call(akka.japi.function.Function<E, KeyValueEntity.Effect<R>> func) {
    return call(func, Metadata.EMPTY);
  }

  /**
   * The call method can be used to simulate a call to the KeyValueEntity. The passed java lambda
   * should return a KeyValueEntity.Effect. The Effect is interpreted into a KeyValueEntityResult that can
   * be used in test assertions.
   *
   * @param func     A function from KeyValueEntity to KeyValueEntity.Effect.
   * @param metadata A metadata passed as a call context.
   * @param <R>      The type of reply that is expected from invoking a command handler
   * @return a KeyValueEntityResult
   * @deprecated Use "method(MyEntity::myCommandHandler).withMetadata(metadata).invoke()" instead
   */
  @Deprecated(since = "3.2.1", forRemoval = true)
  public <R> KeyValueEntityResult<R> call(akka.japi.function.Function<E, KeyValueEntity.Effect<R>> func, Metadata metadata) {
    return call(func, metadata, Optional.empty());
  }

  private <R> KeyValueEntityResult<R> call(akka.japi.function.Function<E, KeyValueEntity.Effect<R>> func, Metadata metadata, Optional<Class<?>> returnType) {
    TestKitKeyValueEntityCommandContext commandContext =
      new TestKitKeyValueEntityCommandContext(entityId, metadata);
    entity._internalSetCommandContext(Optional.of(commandContext));
    entity._internalSetCurrentState(this.state, this.deleted);
    return interpretEffects(() -> {
      try {
        return func.apply(entity);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }, returnType);
  }
}
