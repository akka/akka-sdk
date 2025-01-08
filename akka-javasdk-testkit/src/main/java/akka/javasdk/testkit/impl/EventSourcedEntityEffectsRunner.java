/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl;

import akka.javasdk.Metadata;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.testkit.EventSourcedResult;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

/** Extended by generated code, not meant for user extension */
public abstract class EventSourcedEntityEffectsRunner<S, E> {

  private EventSourcedEntity<S, E> entity;
  private S _state;
  private List<E> events = new ArrayList<>();

  public EventSourcedEntityEffectsRunner(EventSourcedEntity<S, E> entity) {
    this.entity = entity;
    this._state = entity.emptyState();
  }

  public EventSourcedEntityEffectsRunner(EventSourcedEntity<S, E> entity, S initialState) {
    this.entity = entity;
    this._state = initialState;
  }

  public EventSourcedEntityEffectsRunner(EventSourcedEntity<S, E> entity, List<E> initialEvents) {
    this.entity = entity;
    this._state = entity.emptyState();

    entity._internalSetCurrentState(this._state);
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

  /** @return All events emitted by command handlers of this entity up to now */
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
      Supplier<EventSourcedEntity.Effect<R>> effect, String entityId, Metadata metadata) {
    var commandContext = new TestKitEventSourcedEntityCommandContext(entityId, metadata);
    EventSourcedEntity.Effect<R> effectExecuted;
    try {
      entity._internalSetCommandContext(Optional.of(commandContext));
      entity._internalSetCurrentState(this._state);
      effectExecuted = effect.get();
      this.events.addAll(EventSourcedResultImpl.eventsOf(effectExecuted));
    } finally {
      entity._internalSetCommandContext(Optional.empty());
    }

    playEventsForEntity(EventSourcedResultImpl.eventsOf(effectExecuted));

    EventSourcedResult<R> result;
    try {
      entity._internalSetCommandContext(Optional.of(commandContext));
      var secondaryEffect = EventSourcedResultImpl.secondaryEffectOf(effectExecuted, _state);
      result = new EventSourcedResultImpl<>(effectExecuted, _state, secondaryEffect);
    } finally {
      entity._internalSetCommandContext(Optional.empty());
    }
    return result;
  }

  private void playEventsForEntity(List<E> events) {
    try {
      entity._internalSetEventContext(Optional.of(new TestKitEventSourcedEntityEventContext()));
      for (E event : events) {
        this._state = handleEvent(this._state, event);
        entity._internalSetCurrentState(this._state);
      }
    } finally {
      entity._internalSetEventContext(Optional.empty());
    }
  }
}
