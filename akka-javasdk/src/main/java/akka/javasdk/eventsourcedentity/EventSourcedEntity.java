/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.eventsourcedentity;

import akka.javasdk.Metadata;
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The Event Sourced state model captures changes to data by storing events in a journal.
 * The current entity state is derived from the emitted events.
 * <p>
 * When implementing an Event Sourced Entity, you first define what will be its internal state (your domain model),
 * the commands it will handle and the events it will emit to modify its state.
 * <p>
 * Each command is handled by a command handler. Command handlers are methods returning an {@link Effect}.
 * When handling a command, you use the Effect API to:
 * <p>
 * <ul>
 *   <li>emit events and build a reply
 *   <li>directly returning to the caller if the command is not requesting any state change
 *   <li>rejected the command by returning an error
 *   <li>instruct the runtime to delete the entity
 * </ul>
 *
 * <p>Each event is handled by the {@link #applyEvent(E)} method.
 * Events are required to inherit from a common sealed interface, and it's recommend to implement the {@link #applyEvent(E)} method using a switch statement.
 * As such, the compiler can check if all existing events are being handled.
 *
 *<pre>
 * {@code
 * // example of sealed event interface with concrete events implementing it
 * public sealed interface Event {
 *   public record UserCreated(String name, String email) implements Event {};
 *   public record EmailUpdated(String newEmail) implements Event {};
 * }
 *
 * // example of applyEvent implementation
 * public User applyEvent(Event event) {
 *    return switch (event) {
 *      case UserCreated userCreated -> new User(userCreated.name, userCreated.email);
 *      case EmailUpdated emailUpdated -> this.copy(email = emailUpdated.newEmail);
 *    }
 * }
 * }
 *</pre>
 * <p>
 * Concrete classes can accept the following types to the constructor:
 * <ul>
 *   <li>{@link akka.javasdk.eventsourcedentity.EventSourcedEntityContext}</li>
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider} from the service setup</li>
 * </ul>
 *
 *
 * @param <S> The type of the state for this entity.
 * @param <E> The parent type of the event hierarchy for this entity. Required to be a sealed interface.
 */
public abstract class EventSourcedEntity<S, E> {

  private Optional<CommandContext> commandContext = Optional.empty();
  private Optional<EventContext> eventContext = Optional.empty();
  private Optional<S> currentState = Optional.empty();
  private boolean handlingCommands = false;

  /**
   * Implement by returning the initial empty state object. This object will be made available
   * through the {@link #currentState()} method. This method is only called when the entity is initializing and
   * there isn't yet a known state.
   *
   * <p>Also known as "zero state" or "neutral state".
   *
   * <p>The default implementation of this method returns <code>null</code>. It can be overridden to
   * return a more sensible initial state.
   */
  public S emptyState() {
    return null;
  }

  /**
   * Additional context and metadata for a command handler.
   *
   * <p>It will throw an exception if accessed from constructor or inside the {@link #applyEvent(E)} method.
   */
  protected final CommandContext commandContext() {
    return commandContext.orElseThrow(
        () ->
            new IllegalStateException("CommandContext is only available when handling a command."));
  }

  /** INTERNAL API */
  public void _internalSetCommandContext(Optional<CommandContext> context) {
    commandContext = context;
  }

  /**
   * Additional context and metadata when handling an event in the {@link #applyEvent(E)} method.
   *
   * <p>It will throw an exception if accessed from constructor or command handler.
   */
  protected final EventContext eventContext() {
    return eventContext.orElseThrow(
        () -> new IllegalStateException("EventContext is only available when handling an event."));
  }

  /** INTERNAL API */
  public void _internalSetEventContext(Optional<EventContext> context) {
    eventContext = context;
  }

  /** INTERNAL API */
  public void _internalSetCurrentState(S state) {
    handlingCommands = true;
    currentState = Optional.ofNullable(state);
  }

  /**
   * This is the main event handler method. Whenever an event is emitted, this handler will be called.
   * It should return the new state of the entity.
   *
   * Note that this method is called in two situations:
   * <ul>
   *     <li>when one or more events are emitted by the command handler, this method is called to produce
   *     the new state of the entity.
   *     <li>when instantiating an entity from the event journal, this method is called to restore the state of the entity.
   * </ul>
   *
   * It's important to keep the event handler side effect free. This means that it should only apply the event
   * on the current state and return the updated state. This is because the event handler is called during recovery.
   *
   * Events are required to inherit from a common sealed interface, and it's recommend to implement this method using a switch statement.
   * As such, the compiler can check if all existing events are being handled.
   *
   *<pre>
   * {@code
   * // example of sealed event interface with concrete events implementing it
   * public sealed interface Event {
   *   public record UserCreated(String name, String email) implements Event {};
   *   public record EmailUpdated(String newEmail) implements Event {};
   * }
   *
   * // example of applyEvent implementation
   * public User applyEvent(Event event) {
   *    return switch (event) {
   *      case UserCreated userCreated -> new User(userCreated.name, userCreated.email);
   *      case EmailUpdated emailUpdated -> this.copy(email = emailUpdated.newEmail);
   *    }
   * }
   * }
   *</pre>
   *
   */
  public abstract S applyEvent(E event);

  /**
   * Returns the state as currently stored.
   *
   * <p>Note that modifying the state directly will not update it in storage.
   * The state can only be updated through the {@link #applyEvent(E)} method.
   *
   * <p>This method can only be called when handling a command or an event. Calling it outside a
   * method (eg: in the constructor) will raise a IllegalStateException exception.
   *
   * @throws IllegalStateException if accessed outside a handler method
   */
  protected final S currentState() {
    // user may call this method inside a command handler and get a null because it's legal
    // to have emptyState set to null.
    if (handlingCommands) return currentState.orElse(null);
    else
      throw new IllegalStateException("Current state is only available when handling a command.");
  }

  protected final Effect.Builder<S, E> effects() {
    return new EventSourcedEntityEffectImpl<S, E>();
  }

  /**
   * An Effect is a description of what the runtime needs to do after the command is handled.
   * You can think of it as a set of instructions you are passing to the runtime, which will process
   * the instructions on your behalf.
   * <p>
   * Each component defines its own effects, which are a set of predefined
   * operations that match the capabilities of that component.
   * <p>
   * An EventSourcedEntity Effect can either:
   * <p>
   * <ul>
   *   <li>emit events and send a reply to the caller
   *   <li>directly reply to the caller if the command is not requesting any state change
   *   <li>rejected the command by returning an error
   *   <li>instruct the runtime to delete the entity
   * </ul>
   * <p>
   *
   * @param <T> The type of the message that must be returned by this call.
   */
  public interface Effect<T> {

    /**
     * Construct the effect that is returned by the command handler. The effect describes next
     * processing actions, such as emitting events and sending a reply.
     *
     * @param <S> The type of the state for this entity.
     */
    interface Builder<S, E> {

      /**
       * Persist a single event.
       * After this event is persisted, the event handler {@link #applyEvent(E event)} is called in order to update the entity state.
       */
      OnSuccessBuilder<S> persist(E event);

      /**
       * Persist the passed events.
       * After these events are persisted, the event handler {@link #applyEvent(E event)} is called in order to update the entity state.
       * Note, the event handler is called only once after all events are persisted.
       */
      OnSuccessBuilder<S> persist(E event1, E event2, E... events);

      /**
       * Persist the passed List of events.
       * After these events are persisted, the event handler {@link #applyEvent(E event)} is called in order to update the entity state.
       * Note, the event handler is called only once after all events are persisted.
       */
      OnSuccessBuilder<S> persistAll(List<? extends E> events);

      /**
       * Create a message reply.
       *
       * @param message The payload of the reply.
       * @return A message reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> ReadOnlyEffect<T> reply(T message);

      /**
       * Create a message reply.
       *
       * @param message The payload of the reply.
       * @param metadata The metadata for the message.
       * @return A message reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> ReadOnlyEffect<T> reply(T message, Metadata metadata);

      /**
       * Create an error reply.
       *
       * @param description The description of the error.
       * @return An error reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> ReadOnlyEffect<T> error(String description);

    }

    interface OnSuccessBuilder<S> {

      /**
       * Delete the entity. No addition events are allowed.
       */
      OnSuccessBuilder<S> deleteEntity();

      /**
       * Reply after for example <code>emitEvent</code>.
       *
       * @param replyMessage Function to create the reply message from the new state.
       * @return A message reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> Effect<T> thenReply(Function<S, T> replyMessage);

      /**
       * Reply after for example <code>emitEvent</code>.
       *
       * @param replyMessage Function to create the reply message from the new state.
       * @param metadata The metadata for the message.
       * @return A message reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> Effect<T> thenReply(Function<S, T> replyMessage, Metadata metadata);

    }

  }

  /**
   * An effect that is known to be read only and does not update the state of the entity.
   */
  public interface ReadOnlyEffect<T> extends Effect<T> {
  }
}
