/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.keyvalueentity;

import akka.annotation.InternalApi;
import akka.javasdk.CommandException;
import akka.javasdk.Metadata;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.impl.keyvalueentity.KeyValueEntityEffectImpl;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Key Value Entities are stateful components that persist their complete state on every change.
 * Unlike Event Sourced Entities, only the latest state is stored without access to historical
 * changes.
 *
 * <p>Key Value Entities provide strong consistency guarantees through entity sharding, where each
 * entity instance is identified by a unique id and distributed across the service cluster. Only one
 * instance of each entity exists in the cluster at any time, ensuring sequential message processing
 * without concurrency concerns.
 *
 * <p>The entity state is kept in memory while active and can serve read requests or command
 * validation without additional reads from durable storage. Inactive entities are passivated and
 * recover their state from durable storage when accessed again.
 *
 * <h2>Implementation Steps</h2>
 *
 * <ol>
 *   <li>Define the entity's state
 *   <li>Implement behavior in command handlers
 * </ol>
 *
 * <h2>Command Handlers</h2>
 *
 * Command handlers are methods that return an {@link Effect} and define how the entity responds to
 * commands. The Effect API allows you to:
 *
 * <ul>
 *   <li>Update the entity state and send a reply to the caller
 *   <li>Reply directly without state changes
 *   <li>Return an error message
 *   <li>Delete the entity
 * </ul>
 *
 * <h2>Example Implementation</h2>
 *
 * <pre>{@code
 * @Component(id = "counter")
 * public class CounterEntity extends KeyValueEntity<Counter> {
 *
 *   public CounterEntity(KeyValueEntityContext context) {
 *     // Constructor can accept KeyValueEntityContext and custom dependency types
 *   }
 *
 *   @Override
 *   public Counter emptyState() {
 *     return new Counter(0);
 *   }
 *
 *   public Effect<Counter> set(int value) {
 *     Counter newCounter = new Counter(value);
 *     return effects()
 *         .updateState(newCounter)
 *         .thenReply(newCounter);
 *   }
 *
 *   public ReadOnlyEffect<Counter> get() {
 *     return effects().reply(currentState());
 *   }
 * }
 * }</pre>
 *
 * <p>Concrete classes can accept the following types in the constructor:
 *
 * <ul>
 *   <li>{@link KeyValueEntityContext} - provides entity context information
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider} from the service setup
 * </ul>
 *
 * <p>Concrete classes must be annotated with {@link akka.javasdk.annotations.ComponentId} with a
 * stable, unique identifier that cannot be changed after production deployment.
 *
 * <h2>Multi-region Replication</h2>
 *
 * Key Value Entities support multi-region replication for resilience and performance. Write
 * requests are handled by the primary region, while read requests can be served from any region.
 * Use {@link ReadOnlyEffect} for read-only operations that can be served from replicas.
 *
 * <h2>Immutable state record</h2>
 *
 * <p>It is recommended to use immutable state objects, such as Java records, for the entity state.
 * Immutable state ensures thread safety and prevents accidental modifications that could lead to
 * inconsistent state or concurrency issues.
 *
 * <p>While mutable state classes are supported, they require careful handling:
 *
 * <ul>
 *   <li>Mutable state should not be shared outside the entity
 *   <li>Mutable state should not be passed to other threads, such as in {@code CompletionStage}
 *       operations
 *   <li>Any modifications to mutable state must be done within the entity's command handlers
 * </ul>
 *
 * <p><strong>Collections in State:</strong> Collections (such as {@code List}, {@code Set}, {@code
 * Map}) are typically mutable even when contained within immutable objects. When updating state
 * that contains collections, you should create copies of the collections rather than modifying them
 * in place. This ensures that the previous state remains unchanged and prevents unintended side
 * effects.
 *
 * <p><strong>Performance Considerations:</strong> Defensive copying of collections can introduce
 * performance overhead, especially for large collections or frequent updates. In
 * performance-critical scenarios, this recommendation can be carefully tuned by using mutable state
 * with strict adherence to the safety guidelines mentioned above.
 *
 * <p>Using immutable records with defensive copying of collections eliminates concurrency concerns
 * and is the preferred approach for state modeling in most cases.
 *
 * @param <S> The type of the state for this entity
 */
public abstract class KeyValueEntity<S> {

  private Optional<CommandContext> commandContext = Optional.empty();

  private Optional<S> currentState = Optional.empty();

  private boolean deleted = false;

  private boolean handlingCommands = false;

  /**
   * Returns the initial empty state object for this entity. This state is used when the entity is
   * first created and before any commands have been processed.
   *
   * <p>Also known as "zero state" or "neutral state". This method is called when the entity is
   * instantiated for the first time or when recovering from storage without any persisted state.
   *
   * <p>The default implementation returns {@code null}. Override this method to provide a more
   * meaningful initial state for your entity.
   *
   * @return the initial state object, or {@code null} if no initial state is defined
   */
  public S emptyState() {
    return null;
  }

  /**
   * Provides access to additional context and metadata for the current command being processed.
   * This includes information such as the command name, entity id, and tracing context.
   *
   * <p>This method can only be called from within a command handler method. Attempting to access it
   * from the constructor or outside of command processing will result in an exception.
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
   *
   * @hidden
   */
  @InternalApi
  public void _internalSetCommandContext(Optional<CommandContext> context) {
    commandContext = context;
  }

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public void _internalSetCurrentState(S state, boolean deleted) {
    handlingCommands = true;
    currentState = Optional.ofNullable(state);
    this.deleted = deleted;
  }

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public void _internalClearCurrentState() {
    handlingCommands = false;
    currentState = Optional.empty();
  }

  /**
   * Returns the current state of this entity as stored in memory. This represents the latest
   * persisted state and any updates made during the current command processing.
   *
   * <p><strong>Important:</strong> Modifying the returned state object directly will not persist
   * the changes. To save state changes, you must use {@code effects().updateState(newState)} in
   * your command handler's return value.
   *
   * <p>This method can only be called from within a command handler method. Attempting to access it
   * from the constructor or outside of command processing will result in an exception.
   *
   * @return the current state of the entity, which may initially be {@code null} if {@link
   *     #emptyState} has not been defined
   * @throws IllegalStateException if accessed outside a command handler method
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
   * {@code effects().deleteEntity()}, it will still exist with an empty state for some time before
   * being completely removed.
   *
   * <p>After deletion, the entity can still handle read requests but will have an empty state. No
   * further state changes are allowed after deletion. The entity will be completely cleaned up
   * after a default period of one week.
   *
   * @return {@code true} if the entity has been deleted, {@code false} otherwise
   */
  protected boolean isDeleted() {
    return deleted;
  }

  protected final Effect.Builder<S> effects() {
    return new KeyValueEntityEffectImpl<S>();
  }

  /**
   * An Effect describes the actions that the Akka runtime should perform after a command handler
   * completes. Effects are declarative instructions that tell the runtime how to update state, send
   * replies, or handle errors.
   *
   * <p>Key Value Entity effects can:
   *
   * <ul>
   *   <li>Update the entity state and send a reply to the caller
   *   <li>Reply directly to the caller without state changes
   *   <li>Return an error message to reject the command
   *   <li>Delete the entity from storage
   * </ul>
   *
   * @param <T> The type of the message that must be returned by this call
   */
  public interface Effect<T> {

    /**
     * Builder for constructing effects that describe the actions to be performed after command
     * processing. Use this to create effects that update state, reply to callers, or handle errors.
     *
     * @param <S> The type of the state for this entity
     */
    interface Builder<S> {

      /**
       * Updates the entity state with the provided new state. The new state will replace the
       * current state entirely.
       *
       * @param newState the new state to persist, replacing the current state
       * @return a builder for chaining additional effects like replies
       */
      OnSuccessBuilder<S> updateState(S newState);

      /**
       * Updates the entity state with the provided new state and additional metadata together with
       * the state. The new state will replace the current state entirely.
       *
       * @param newState the new state to persist, replacing the current state
       * @return a builder for chaining additional effects like replies
       */
      OnSuccessBuilder<S> updateStateWithMetadata(S newState, Metadata metadata);

      /**
       * Marks the entity for deletion. After deletion, the entity will still exist with an empty
       * state for some time before being completely removed. No additional state updates are
       * allowed after deletion.
       *
       * @return a builder for chaining additional effects like replies
       */
      OnSuccessBuilder<S> deleteEntity();

      /**
       * Creates a reply message to send back to the caller without updating the entity state. Use
       * this for read-only operations or when the command doesn't require state changes.
       *
       * @param message the payload of the reply message
       * @param <T> the type of the reply message
       * @return a read-only effect containing the reply
       */
      <T> ReadOnlyEffect<T> reply(T message);

      /**
       * Creates a reply message with additional metadata to send back to the caller without
       * updating the entity state.
       *
       * @param message the payload of the reply message
       * @param metadata additional metadata to include with the reply
       * @param <T> the type of the reply message
       * @return a read-only effect containing the reply
       */
      <T> ReadOnlyEffect<T> reply(T message, Metadata metadata);

      /**
       * Creates an error reply to reject the command and inform the caller of the failure. The
       * entity state will not be modified when returning an error.
       *
       * @param message the error message.
       * @param <T> the type of the expected reply message
       * @return a read-only effect containing the error
       */
      <T> ReadOnlyEffect<T> error(String message);

      /**
       * Create an error reply. {@link CommandException} will be serialized and sent to the client.
       * It's possible to catch it with try-catch statement or {@link CompletionStage} API when
       * using async {@link ComponentClient} API.
       *
       * @param commandException The command exception to be returned.
       * @param <T> The type of the message that must be returned by this call.
       * @return An error reply.
       */
      <T> ReadOnlyEffect<T> error(CommandException commandException);
    }

    /**
     * Builder for chaining a reply after a successful state operation like {@code updateState} or
     * {@code deleteEntity}. This allows you to both modify the entity and send a response to the
     * caller in a single effect.
     *
     * @param <S> The type of the state for this entity
     */
    interface OnSuccessBuilder<S> {

      /**
       * Sends a reply message to the caller after the state operation (update or delete) completes
       * successfully. This is typically used to confirm the operation and return the new state or
       * operation result.
       *
       * @param message the payload of the reply message
       * @param <T> the type of the reply message
       * @return an effect that will perform the state operation and then send the reply
       */
      <T> Effect<T> thenReply(T message);

      /**
       * Sends a reply message with additional metadata to the caller after the state operation
       * completes successfully.
       *
       * @param message the payload of the reply message
       * @param metadata additional metadata to include with the reply
       * @param <T> the type of the reply message
       * @return an effect that will perform the state operation and then send the reply
       */
      <T> Effect<T> thenReply(T message, Metadata metadata);
    }
  }

  /**
   * A read-only effect that does not modify the entity state. These effects are used for operations
   * that only read data or send replies without persisting any changes.
   *
   * <p>Read-only effects are important for multi-region replication as they can be served from any
   * region, not just the primary region where writes are handled.
   *
   * @param <T> The type of the message that will be returned by this effect
   */
  public interface ReadOnlyEffect<T> extends Effect<T> {}
}
