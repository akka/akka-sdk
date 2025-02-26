/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl;

import akka.javasdk.Metadata;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.impl.effect.MessageReplyImpl;
import akka.javasdk.impl.reflection.Reflect;
import akka.javasdk.impl.serialization.JsonSerializer;
import akka.javasdk.testkit.EventSourcedResult;
import akka.runtime.sdk.spi.BytesPayload;

import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

/** Extended by generated code, not meant for user extension */
public abstract class EventSourcedEntityEffectsRunner<S, E> {

  protected EventSourcedEntity<S, E> entity;
  private S _state;
  private boolean deleted = false;
  private List<E> events = new ArrayList<>();
  private JsonSerializer jsonSerializer = new JsonSerializer();

  public EventSourcedEntityEffectsRunner(EventSourcedEntity<S, E> entity) {
    this.entity = entity;
    this._state = entity.emptyState();
  }

  public EventSourcedEntityEffectsRunner(EventSourcedEntity<S, E> entity, S initialState) {
    this.entity = entity;
    var stateClass = Reflect.eventSourcedEntityStateType(entity.getClass());
    verifySerDerWithExpectedType(stateClass, initialState);
    this._state = initialState;
  }

  public EventSourcedEntityEffectsRunner(EventSourcedEntity<S, E> entity, List<E> initialEvents) {
    this.entity = entity;
    this._state = entity.emptyState();

    entity._internalSetCurrentState(this._state, false);
    // NB: updates _state
    playEventsForEntity(initialEvents);
  }

  /** @return The current state of the entity after applying the event */
  protected abstract S handleEvent(S state, E event);

  protected EventSourcedEntity<S, E> entity() { return entity; }

  /** @return The current state of the entity */
  public S getState() {
    return _state;
  }

  /** @return true if the entity is deleted */
  public boolean isDeleted() {
    return deleted;
  }

  /** @return All events persisted by command handlers of this entity up to now */
  public List<E> getAllEvents() {
    return events;
  }

  /**
   * creates a command context to run the commands, then creates an event context to run the events,
   * and finally, creates a command context to run the side effects. It cleans each context after
   * each run.
   *
   * @return the result of the side effects
   */
  protected <R> EventSourcedResult<R> interpretEffects(
      Supplier<EventSourcedEntity.Effect<R>> effect, String entityId, Metadata metadata, Optional<Class<?>> returnType) {
    var commandContext = new TestKitEventSourcedEntityCommandContext(entityId, metadata);
    EventSourcedEntity.Effect<R> effectExecuted;
    try {
      entity._internalSetCommandContext(Optional.of(commandContext));
      entity._internalSetCurrentState(this._state, this.deleted);
      effectExecuted = effect.get();
      this.events.addAll(EventSourcedResultImpl.eventsOf(effectExecuted));
    } finally {
      entity._internalSetCommandContext(Optional.empty());
    }

    playEventsForEntity(EventSourcedResultImpl.eventsOf(effectExecuted));
    deleted = EventSourcedResultImpl.checkIfDeleted(effectExecuted);

    EventSourcedResult<R> result;
    try {
      entity._internalSetCommandContext(Optional.of(commandContext));
      var secondaryEffect = EventSourcedResultImpl.secondaryEffectOf(effectExecuted, _state);
      if (secondaryEffect instanceof MessageReplyImpl){
        var reply = ((MessageReplyImpl) secondaryEffect).message();
        returnType.ifPresent(type -> verifySerDerWithExpectedType(type, reply));
      }
      result = new EventSourcedResultImpl<>(effectExecuted, _state, secondaryEffect);
    } finally {
      entity._internalSetCommandContext(Optional.empty());
    }
    return result;
  }

  protected void verifySerDer(Object object) {
    try {
      BytesPayload bytesPayload = jsonSerializer.toBytes(object);
      jsonSerializer.fromBytes(bytesPayload);
    } catch (Exception e) {
      fail(object, e);
    }
  }

  /**
   * different deserialization for responses, state, and commands
   */
  protected void verifySerDerWithExpectedType(Class<?> expectedClass, Object object) {
    try {
      BytesPayload bytesPayload = jsonSerializer.toBytes(object);
      jsonSerializer.fromBytes(expectedClass, bytesPayload);
    } catch (Exception e) {
      fail(object, e);
    }
  }

  private void fail(Object object, Exception e) {
    throw new RuntimeException("Failed to serialize or deserialize " + object.getClass().getName() + ". Make sure that all events, commands, responses and state are serializable for " + entity.getClass().getName(), e);
  }

  private void playEventsForEntity(List<E> events) {
    try {
      entity._internalSetEventContext(Optional.of(new TestKitEventSourcedEntityEventContext()));
      for (E event : events) {
        verifySerDer(event);
        this._state = handleEvent(this._state, event);
        entity._internalSetCurrentState(this._state, this.deleted);
      }
    } finally {
      entity._internalSetEventContext(Optional.empty());
    }
  }
}
