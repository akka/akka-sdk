/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.keyvalueentity;

import akka.annotation.InternalApi;
import akka.javasdk.Metadata;
import akka.javasdk.impl.keyvalueentity.KeyValueEntityEffectImpl;

import java.util.Optional;

/**
 * Key Value Entities persist their state on every change. You can think of them as a Key-Value entity where
 * the key is the entity id and the value is the state of the entity.
 * <p>
 *
 * When implementing a Key Value Entity, you first define what will be its internal state (your domain model),
 * and the commands it will handle (mutation requests).
 * <p>
 * Each command is handled by a command handler. Command handlers are methods returning an {@link Effect}.
 * When handling a command, you use the Effect API to:
 * <p>
 * <ul>
 *   <li>update the entity state and send a reply to the caller
 *   <li>directly reply to the caller if the command is not requesting any state change
 *   <li>rejected the command by returning an error
 *   <li>instruct the runtime to delete the entity
 * </ul>
 * <p>
 * Concrete classes can accept the following types to the constructor:
 * <ul>
 *   <li>{@link akka.javasdk.keyvalueentity.KeyValueEntityContext}</li>
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider} from the service setup</li>
 * </ul>
 * <p>
 * Concrete class must be annotated with {@link akka.javasdk.annotations.ComponentId}.
 *
 * @param <S> The type of the state for this entity. */
public abstract class KeyValueEntity<S> {

  private Optional<CommandContext> commandContext = Optional.empty();

  private Optional<S> currentState = Optional.empty();

  private boolean deleted = false;

  private boolean handlingCommands = false;

  /**
   * Implement by returning the initial empty state object. This object will be passed into the
   * command handlers, until a new state replaces it.
   *
   * <p>Also known as "zero state" or "neutral state".
   *
   * <p>The default implementation of this method returns {@code null}. It can be overridden to
   * return a more sensible initial state.
   */
  public S emptyState() {
    return null;
  }

  /**
   * Additional context and metadata for a command handler.
   *
   * <p>It will throw an exception if accessed from constructor.
   *
   * @throws IllegalStateException if accessed outside a handler method
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
   * INTERNAL API
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
   * @hidden
   */
  @InternalApi
  public void _internalClearCurrentState() {
    handlingCommands = false;
    currentState = Optional.empty();
  }

  /**
   * Returns the state as currently stored.
   *
   * <p>Note that modifying the state directly will not update it in storage. To save the state, you
   * must call {{@code effects().updateState()}}.
   *
   * <p>This method can only be called when handling a command. Calling it outside a method (eg: in
   * the constructor) will raise a IllegalStateException exception.
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

  /**
   * Returns true if the entity has been deleted.
   */
  protected boolean isDeleted() {
    return deleted;
  }

  protected final Effect.Builder<S> effects() {
    return new KeyValueEntityEffectImpl<S>();
  }

  /**
   * An Effect is a description of what the runtime needs to do after the command is handled.
   * You can think of it as a set of instructions you are passing to the runtime, which will process
   * the instructions on your behalf.
   * <p>
   * Each component defines its own effects, which are a set of predefined
   * operations that match the capabilities of that component.
   * <p>
   * a KeyValueEntity Effect can either:
   * <p>
   * <ul>
   *   <li>update the entity state and send a reply to the caller
   *   <li>directly reply to the caller if the command is not requesting any state change
   *   <li>rejected the command by returning an error
   *   <li>instruct the runtime to delete the entity
   * </ul>
   *
   * @param <T> The type of the message that must be returned by this call.
   */
  public interface Effect<T> {

    /**
     * Construct the effect that is returned by the command handler. The effect describes next
     * processing actions, such as updating state and sending a reply.
     *
     * @param <S> The type of the state for this entity.
     */
    interface Builder<S> {

      OnSuccessBuilder<S> updateState(S newState);

      /**
       * Delete the entity. No additional updates are allowed afterwards.
       */
      OnSuccessBuilder<S> deleteEntity();


      /**
       * Create a message reply.
       *
       * @param message The payload of the reply.
       * @param <T> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <T> ReadOnlyEffect<T> reply(T message);

      /**
       * Create a message reply.
       *
       * @param message The payload of the reply.
       * @param metadata The metadata for the message.
       * @param <T> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <T> ReadOnlyEffect<T> reply(T message, Metadata metadata);

      /**
       * Create an error reply.
       *
       * @param description The description of the error.
       * @param <T> The type of the message that must be returned by this call.
       * @return An error reply.
       */
      <T> ReadOnlyEffect<T> error(String description);

    }

    interface OnSuccessBuilder<S> {

      /**
       * Reply after for example {@code updateState}.
       *
       * @param message The payload of the reply.
       * @param <T> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <T> Effect<T> thenReply(T message);

      /**
       * Reply after for example {@code updateState}.
       *
       * @param message The payload of the reply.
       * @param metadata The metadata for the message.
       * @param <T> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <T> Effect<T> thenReply(T message, Metadata metadata);

    }

  }

  /**
   * An effect that is known to be read only and does not update the state of the entity.
   */
  public interface ReadOnlyEffect<T> extends Effect<T> {
  }
}
