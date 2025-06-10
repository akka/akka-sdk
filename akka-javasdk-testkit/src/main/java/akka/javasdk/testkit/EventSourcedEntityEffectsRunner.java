/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.Metadata;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.impl.reflection.Reflect;
import akka.javasdk.testkit.impl.EventSourcedResultImpl;
import akka.javasdk.testkit.impl.TestKitEventSourcedEntityCommandContext;
import akka.javasdk.testkit.impl.TestKitEventSourcedEntityEventContext;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static akka.javasdk.testkit.EntitySerializationChecker.verifySerDer;
import static akka.javasdk.testkit.EntitySerializationChecker.verifySerDerWithExpectedType;

/** Extended by generated code, not meant for user extension */
abstract class EventSourcedEntityEffectsRunner<S, E> {

  private final Class<?> stateClass;
  protected EventSourcedEntity<S, E> entity;
  private S _state;
  private boolean deleted = false;
  private List<E> events = new ArrayList<>();

  public EventSourcedEntityEffectsRunner(EventSourcedEntity<S, E> entity) {
    this.entity = entity;
    this.stateClass = Reflect.eventSourcedEntityStateType(entity.getClass());
    this._state = entity.emptyState();
  }

  public EventSourcedEntityEffectsRunner(EventSourcedEntity<S, E> entity, S initialState) {
    this.entity = entity;
    this.stateClass = Reflect.eventSourcedEntityStateType(entity.getClass());
    verifySerDerWithExpectedType(stateClass, initialState, entity);
    this._state = initialState;
  }

  public EventSourcedEntityEffectsRunner(EventSourcedEntity<S, E> entity, List<E> initialEvents) {
    this.entity = entity;
    this._state = entity.emptyState();
    this.stateClass = Reflect.eventSourcedEntityStateType(entity.getClass());
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
      Supplier<EventSourcedEntity.Effect<R>> effect, String entityId, Metadata metadata, Optional<Type> returnType) {
    var sequenceNumber = this.events.size();
    var commandContext = new TestKitEventSourcedEntityCommandContext(entityId, metadata, sequenceNumber);
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
    deleted = EventSourcedResultImpl.checkIfDeleted(effectExecuted, this.deleted);

    EventSourcedResult<R> result;
    try {
      entity._internalSetCommandContext(Optional.of(commandContext));
      var secondaryEffect = EventSourcedResultImpl.secondaryEffectOf(effectExecuted, _state);
      result = new EventSourcedResultImpl<>(effectExecuted, _state, secondaryEffect);
      if (result.isReply()) {
        returnType.ifPresent(type -> verifySerDerWithExpectedType(type, result.getReply(), entity));
      }
    } finally {
      entity._internalSetCommandContext(Optional.empty());
    }
    return result;
  }

  private void playEventsForEntity(List<E> events) {
    try {
      entity._internalSetEventContext(Optional.of(new TestKitEventSourcedEntityEventContext()));
      for (E event : events) {
        verifySerDer(event, entity);
        this._state = handleEvent(this._state, event);
        verifySerDerWithExpectedType(stateClass, this._state, entity);
        entity._internalSetCurrentState(this._state, this.deleted);
      }
    } finally {
      entity._internalSetEventContext(Optional.empty());
    }
  }
}
