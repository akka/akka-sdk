/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.view;

import akka.annotation.InternalApi;
import akka.javasdk.annotations.Table;
import akka.javasdk.impl.view.ViewEffectImpl;
import java.util.Optional;

/**
 * Responsible for consuming events from a source and emit updates to one view table. Event subject
 * (entity id for entities, cloud event subject for events from a topic) maps one to one with a row
 * in the table.
 *
 * <p>Concrete subclasses should be public static inner classes of the view they update a table for.
 * A public no-parameter constructor must exist and is used to create instances used.
 *
 * <p>For a single table view the table name can be inferred from queries, but for a multi table
 * view each class must be annotated with {@link Table} identifying which table they update.
 *
 * <p>Concrete class must be annotated with one of the {@link akka.javasdk.annotations.Consume}
 * annotations.
 *
 * @param <S> The type of each row in the table updated by this updater.
 */
public abstract class TableUpdater<S> {

  private Optional<UpdateContext> updateContext = Optional.empty();

  private Optional<S> viewState = Optional.empty();

  private boolean handlingUpdates = false;

  /**
   * Additional context and metadata for an update handler.
   *
   * <p>It will throw an exception if accessed from constructor.
   */
  protected final UpdateContext updateContext() {
    return updateContext.orElseThrow(
        () ->
            new IllegalStateException("UpdateContext is only available when handling an update."));
  }

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public void _internalSetUpdateContext(Optional<UpdateContext> context) {
    updateContext = context;
  }

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public void _internalSetViewState(S state) {
    handlingUpdates = true;
    viewState = Optional.ofNullable(state);
  }

  /**
   * Returns the current state of the row for the subject that is being updated.
   *
   * <p>Note that modifying the row object directly will not update it in storage. To save the row,
   * one must call {{@code effects().updateRow()}}.
   *
   * <p>This method can only be called when handling an update. Calling it outside a method (eg: in
   * the constructor) will raise a IllegalStateException exception.
   *
   * @throws IllegalStateException if accessed outside a handler method
   */
  protected final S rowState() {
    // user may call this method inside a command handler and get a null because it's legal
    // to have emptyState set to null.
    if (handlingUpdates) return viewState.orElse(null);
    else throw new IllegalStateException("Current state is only available when handling updates.");
  }

  protected final Effect.Builder<S> effects() {
    return ViewEffectImpl.<S>builder();
  }

  /**
   * The default implementation of this method returns {@code null}. It can be overridden to return
   * a more sensible initial state.
   *
   * @return an empty row object or {@code null} to hand to the process method when an event for a
   *     previously unknown subject id is seen.
   */
  public S emptyRow() {
    return null;
  }

  /**
   * An UpdateEffect is a description of what the runtime needs to do after the command is handled.
   * You can think of it as a set of instructions you are passing to the runtime, which will process
   * the instructions on your behalf.
   *
   * <p>Each component defines its own effects, which are a set of predefined operations that match
   * the capabilities of that component.
   *
   * <p>A View UpdateEffect can either:
   *
   * <p>
   *
   * <ul>
   *   <li>update the view row
   *   <li>delete the view row
   *   <li>ignore the event or state change notification (and not update the view state)
   * </ul>
   *
   * <p>Construct the effect that is returned by the command handler. The effect describes next
   * processing actions, such as emitting events and sending a reply.
   *
   * @param <S> The type of the row for this table.
   */
  public interface Effect<S> {

    interface Builder<S> {

      Effect<S> updateRow(S newRow);

      Effect<S> deleteRow();

      /** Ignore this event (and continue to process the next). */
      Effect<S> ignore();
    }
  }
}
