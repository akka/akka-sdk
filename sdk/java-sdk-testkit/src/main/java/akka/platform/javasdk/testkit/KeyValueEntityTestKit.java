/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit;

import akka.platform.javasdk.Metadata;
import akka.platform.javasdk.testkit.impl.TestKitKeyValueEntityCommandContext;
import akka.platform.javasdk.testkit.impl.TestKitKeyValueEntityContext;
import akka.platform.javasdk.testkit.impl.KeyValueEntityResultImpl;
import akka.platform.javasdk.keyvalueentity.KeyValueEntity;
import akka.platform.javasdk.keyvalueentity.KeyValueEntityContext;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

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

  private S state;
  private final S emptyState;
  private final E entity;
  private final String entityId;

  private KeyValueEntityTestKit(String entityId, E entity) {
    this.entityId = entityId;
    this.entity = entity;
    this.state = entity.emptyState();
    this.emptyState = state;
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

  /** Creates a new testkit instance from a user defined entity id and a KeyValueEntity Supplier. */
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

  /** @return The current state of the key value entity under test */
  public S getState() {
    return state;
  }

  private <Reply> KeyValueEntityResult<Reply> interpretEffects(KeyValueEntity.Effect<Reply> effect) {
    @SuppressWarnings("unchecked")
    KeyValueEntityResultImpl<Reply> result = new KeyValueEntityResultImpl<>(effect);
    if (result.stateWasUpdated()) {
      this.state = (S) result.getUpdatedState();
    } else if (result.stateWasDeleted()) {
      this.state = emptyState;
    }
    return result;
  }

  /**
   * The call method can be used to simulate a call to the KeyValueEntity. The passed java lambda
   * should return a KeyValueEntity.Effect. The Effect is interpreted into a KeyValueEntityResult that can
   * be used in test assertions.
   *
   * @param func A function from KeyValueEntity to KeyValueEntity.Effect.
   * @return a KeyValueEntityResult
   * @param <R> The type of reply that is expected from invoking a command handler
   */
  public <R> KeyValueEntityResult<R> call(Function<E, KeyValueEntity.Effect<R>> func) {
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
   */
  public <R> KeyValueEntityResult<R> call(Function<E, KeyValueEntity.Effect<R>> func, Metadata metadata) {
    TestKitKeyValueEntityCommandContext commandContext =
        new TestKitKeyValueEntityCommandContext(entityId, metadata);
    entity._internalSetCommandContext(Optional.of(commandContext));
    entity._internalSetCurrentState(this.state);
    return interpretEffects(func.apply(entity));
  }
}
