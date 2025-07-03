/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.eventsourcedentity;

import akka.annotation.InternalApi;
import akka.javasdk.Metadata;
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event Sourced Entities are stateful components that persist changes as events in a journal rather than
 * storing the current state directly. The current entity state is derived by replaying all persisted events.
 * This approach provides a complete audit trail and enables reliable state replication.
 * 
 * <p>Event Sourced Entities provide strong consistency guarantees through entity sharding, where each
 * entity instance is identified by a unique ID and distributed across the service cluster. Only one
 * instance of each entity exists in the cluster at any time, ensuring sequential message processing
 * without concurrency concerns.
 * 
 * <p>The entity state is kept in memory while active and can serve read requests or command validation
 * without additional reads from the journal. Inactive entities are passivated and recover their state
 * by replaying events from the journal when accessed again.
 * 
 * <h2>Implementation Steps</h2>
 * <ol>
 *   <li>Model the entity's state and its domain events</li>
 *   <li>Implement behavior in command and event handlers</li>
 * </ol>
 * 
 * <h2>Event Sourcing Model</h2>
 * <p>Unlike traditional CRUD systems, Event Sourced Entities never update state directly. Instead:
 * <ul>
 *   <li>Commands validate business rules and persist events representing state changes</li>
 *   <li>Events are applied to update the entity state through the {@link #applyEvent(E)} method</li>
 *   <li>The current state is always derived from the complete sequence of events</li>
 * </ul>
 * 
 * <h2>Command Handlers</h2>
 * Command handlers are methods that return an {@link Effect} and define how the entity responds to commands.
 * The Effect API allows you to:
 * <ul>
 *   <li>Persist events and send a reply to the caller</li>
 *   <li>Reply directly without persisting events</li>
 *   <li>Return an error message</li>
 *   <li>Delete the entity</li>
 * </ul>
 * 
 * <h2>Event Handlers</h2>
 * <p>Events must inherit from a common sealed interface, and the {@link #applyEvent(E)} method should
 * be implemented using a switch statement for compile-time completeness checking.
 * 
 * <h2>Snapshots</h2>
 * <p>Akka automatically creates snapshots as an optimization to avoid replaying all events during
 * entity recovery. Snapshots are created after a configurable number of events and are handled
 * transparently without requiring specific code.
 * 
 * <h2>Multi-region Replication</h2>
 * Event Sourced Entities support multi-region replication for resilience and performance. Write requests
 * are handled by the primary region, while read requests can be served from any region. Use
 * {@link ReadOnlyEffect} for read-only operations that can be served from replicas.
 * 
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * @ComponentId("shopping-cart")
 * public class ShoppingCartEntity extends EventSourcedEntity<ShoppingCart, ShoppingCartEvent> {
 *   
 *   public ShoppingCartEntity(EventSourcedEntityContext context) {
 *     // Constructor can accept EventSourcedEntityContext and custom dependency types
 *   }
 *   
 *   @Override
 *   public ShoppingCart emptyState() {
 *     return new ShoppingCart(entityId, Collections.emptyList(), false);
 *   }
 *   
 *   public Effect<Done> addItem(LineItem item) {
 *     var event = new ShoppingCartEvent.ItemAdded(item);
 *     return effects()
 *         .persist(event)
 *         .thenReply(newState -> Done.getInstance());
 *   }
 *   
 *   @Override
 *   public ShoppingCart applyEvent(ShoppingCartEvent event) {
 *     return switch (event) {
 *       case ShoppingCartEvent.ItemAdded evt -> currentState().onItemAdded(evt);
 *       case ShoppingCartEvent.ItemRemoved evt -> currentState().onItemRemoved(evt);
 *       case ShoppingCartEvent.CheckedOut evt -> currentState().onCheckedOut();
 *     };
 *   }
 * }
 * }</pre>
 * 
 * <p>Concrete classes can accept the following types in the constructor:
 * <ul>
 *   <li>{@link EventSourcedEntityContext} - provides entity context information</li>
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider} from the service setup</li>
 * </ul>
 * 
 * <p>Concrete classes must be annotated with {@link akka.javasdk.annotations.ComponentId} with a
 * stable, unique identifier that cannot be changed after production deployment.
 *
 * <h2>Immutable state record</h2>
 * <p>It is recommended to use immutable state objects, such as Java records, for the entity state.
 * Immutable state ensures thread safety and prevents accidental modifications that could lead to
 * inconsistent state or concurrency issues.
 *
 * <p>While mutable state classes are supported, they require careful handling:
 * <ul>
 *   <li>Mutable state should not be shared outside the entity</li>
 *   <li>Mutable state should not be passed to other threads, such as in {@code CompletionStage} operations</li>
 *   <li>Any modifications to mutable state must be done within the entity's event handler</li>
 * </ul>
 *
 * <p><strong>Collections in State:</strong> Collections (such as {@code List}, {@code Set}, {@code Map}) are
 * typically mutable even when contained within immutable objects. When updating state that contains collections,
 * you should create copies of the collections rather than modifying them in place. This ensures that the
 * previous state remains unchanged and prevents unintended side effects.
 *
 * <p><strong>Performance Considerations:</strong> Defensive copying of collections can introduce performance
 * overhead, especially for large collections or frequent updates. In performance-critical scenarios, this
 * recommendation can be carefully tuned by using mutable state with strict adherence to the safety guidelines
 * mentioned above.
 *
 * <p>Using immutable records with defensive copying of collections eliminates concurrency concerns and is the
 * preferred approach for state modeling in most cases.
 *
 * @param <S> The type of the state for this entity
 * @param <E> The parent type of the event hierarchy for this entity, required to be a sealed interface
 */
public abstract class EventSourcedEntity<S, E> {

  private Optional<CommandContext> commandContext = Optional.empty();
  private Optional<EventContext> eventContext = Optional.empty();
  private Optional<S> currentState = Optional.empty();
  private boolean deleted = false;
  private boolean handlingCommands = false;

  /**
   * Returns the initial empty state object for this entity. This state is used when the entity
   * is first created and before any events have been persisted and applied.
   *
   * <p>Also known as "zero state" or "neutral state". This method is called when the entity
   * is instantiated for the first time or when recovering from the journal without any persisted events.
   *
   * <p>The default implementation returns {@code null}. Override this method to provide a more
   * meaningful initial state for your entity.
   *
   * @return the initial state object, or {@code null} if no initial state is needed
   */
  public S emptyState() {
    return null;
  }

  /**
   * Provides access to additional context and metadata for the current command being processed.
   * This includes information such as the command name, entity ID, sequence number, and tracing context.
   *
   * <p>This method can only be called from within a command handler method. Attempting to access
   * it from the constructor or inside the {@link #applyEvent(E)} method will result in an exception.
   *
   * @return the command context for the current command
   * @throws IllegalStateException if accessed outside a command handler method
   */
  protected final CommandContext commandContext() {
    return commandContext.orElseThrow(
        () ->
            new IllegalStateException("CommandContext is only available when handling a command."));
  }

  /**
   * INTERNAL API
   * @hidden
   */
  @InternalApi
  public void _internalSetCommandContext(Optional<CommandContext> context) {
    commandContext = context;
  }

  /**
   * Provides access to additional context and metadata when handling an event in the {@link #applyEvent(E)} method.
   * This includes information such as the sequence number of the event being processed.
   *
   * <p>This method can only be called from within the {@link #applyEvent(E)} method. Attempting to access
   * it from the constructor or command handler will result in an exception.
   *
   * @return the event context for the current event being processed
   * @throws IllegalStateException if accessed outside the {@link #applyEvent(E)} method
   */
  protected final EventContext eventContext() {
    return eventContext.orElseThrow(
        () -> new IllegalStateException("EventContext is only available when handling an event."));
  }

  /**
   * INTERNAL API
   * @hidden
   */
  @InternalApi
  public void _internalSetEventContext(Optional<EventContext> context) {
    eventContext = context;
  }

  /**
   * INTERNAL API
   * @hidden
   * @return true if this was the first (outer) call to set the state, the caller is then
   *         responsible for finally calling _internalClearCurrentState
   */
  @InternalApi
  public boolean _internalSetCurrentState(S state, boolean deleted) {
    var wasHandlingCommands = handlingCommands;
    handlingCommands = true;
    currentState = Optional.ofNullable(state);
    this.deleted = deleted;
    return !wasHandlingCommands;
  }

  /**
   * INTERNAL API
   * @hidden
   */
  @InternalApi
  public void _internalClearCurrentState() {
    handlingCommands = false;
    currentState = Optional.empty();
  }

  /**
   * This is the main event handler method. Whenever an event is persisted, this handler will be called.
   * It should return the new state of the entity.
   * <p>
   * Note that this method is called in two situations:
   * <ul>
   *     <li>when one or more events are persisted by the command handler, this method is called to produce
   *     the new state of the entity.
   *     <li>when instantiating an entity from the event journal, this method is called to restore the state of the entity.
   * </ul>
   *
   * It's important to keep the event handler side effect free. This means that it should only apply the event
   * on the current state and return the updated state. This is because the event handler is called during recovery.
   * <p>
   * Events are required to inherit from a common sealed interface, and it's recommend to implement this method using a switch statement.
   * As such, the compiler can check if all existing events are being handled.
   *
   *<pre>
   * {@code
   * // example of sealed event interface with concrete events implementing it
   * public sealed interface Event {
   *   @TypeName("created")
   *   public record UserCreated(String name, String email) implements Event {};
   *   @TypeName("email-updated")
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
   * Returns the current state of this entity as derived from all persisted events. This represents
   * the latest state after applying all events in the journal.
   *
   * <p><strong>Important:</strong> Modifying the returned state object directly will not persist
   * the changes. State can only be updated by persisting events through command handlers, which
   * are then applied via the {@link #applyEvent(E)} method.
   *
   * <p>This method can only be called from within a command handler or event handler method.
   * Attempting to access it from the constructor or outside of command/event processing will
   * result in an exception.
   *
   * @return the current state of the entity, which may be {@code null} if no initial state is defined
   * @throws IllegalStateException if accessed outside a handler method
   */
  protected final S currentState() {
    // user may call this method inside a command handler and get a null because it's legal
    // to have emptyState set to null.
    if (handlingCommands) return currentState.orElse(null);
    else
      throw new IllegalStateException("Current state is only available when handling a command.");
  }

  /**
   * Returns whether this entity has been marked for deletion. When an entity is deleted using
   * {@code effects().persist(finalEvent).deleteEntity()}, it will still exist for some time
   * before being completely removed.
   *
   * <p>After deletion, the entity can still handle read requests but no further events can be
   * persisted. The entity and its events will be completely cleaned up after a default period
   * of one week to allow downstream consumers time to process all events.
   *
   * @return {@code true} if the entity has been deleted, {@code false} otherwise
   */
  protected boolean isDeleted() {
    return deleted;
  }

  protected final Effect.Builder<S, E> effects() {
    return new EventSourcedEntityEffectImpl<S, E>();
  }

  /**
   * An Effect describes the actions that the Akka runtime should perform after a command handler
   * completes. Effects are declarative instructions that tell the runtime how to persist events,
   * send replies, or handle errors.
   * 
   * <p>Event Sourced Entity effects can:
   * <ul>
   *   <li>Persist events and send a reply to the caller</li>
   *   <li>Reply directly to the caller without persisting events</li>
   *   <li>Return an error message to reject the command</li>
   *   <li>Delete the entity after persisting a final event</li>
   * </ul>
   *
   * @param <T> The type of the message that must be returned by this call
   */
  public interface Effect<T> {

    /**
     * Construct the effect that is returned by the command handler. The effect describes next
     * processing actions, such as persisting events and sending a reply.
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
       * After these events are persisted, the event handler {@link #applyEvent} is called in order to update the entity state.
       * Note, the event handler is called only once after all events are persisted.
       */
      OnSuccessBuilder<S> persist(E event1, E event2, E... events);

      /**
       * Persist the passed List of events.
       * After these events are persisted, the event handler {@link #applyEvent} is called in order to update the entity state.
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
       * To observe the deletion in consumers and views, persist a final event representing the deletion before
       * triggering delete.
       */
      OnSuccessBuilder<S> deleteEntity();

      /**
       * Reply after for example {@code persist} event.
       *
       * @param replyMessage Function to create the reply message from the new state.
       * @return A message reply.
       * @param <T> The type of the message that must be returned by this call.
       */
      <T> Effect<T> thenReply(Function<S, T> replyMessage);

      /**
       * Reply after for example {@code persist} event.
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
