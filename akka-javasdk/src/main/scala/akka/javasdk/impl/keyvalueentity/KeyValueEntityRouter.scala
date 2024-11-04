/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import java.util.Optional
import KeyValueEntityEffectImpl.DeleteEntity
import KeyValueEntityEffectImpl.UpdateState
import akka.annotation.InternalApi
import akka.javasdk.impl.EntityExceptions
import akka.javasdk.keyvalueentity.CommandContext
import akka.javasdk.keyvalueentity.KeyValueEntity

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object KeyValueEntityRouter {
  final case class CommandResult(effect: KeyValueEntity.Effect[_])

  final case class CommandHandlerNotFound(commandName: String) extends RuntimeException

}

/**
 * @tparam S
 *   the type of the managed state for the entity Not for manual user extension or interaction
 *
 * The concrete {@code KeyValueEntityRouter} is generated for the specific entities defined in Protobuf.
 *
 * INTERNAL API
 */
@InternalApi
private[impl] abstract class KeyValueEntityRouter[S, E <: KeyValueEntity[S]](protected val entity: E) {
  import KeyValueEntityRouter._

  private var state: Option[S] = None

  private def stateOrEmpty(): S = state match {
    case None =>
      val emptyState = entity.emptyState()
      // null is allowed as emptyState
      state = Some(emptyState)
      emptyState
    case Some(state) =>
      state
  }

  /** INTERNAL API */
  // "public" api against the impl/testkit
  final def _internalSetInitState(s: Any): Unit = {
    state = Some(s.asInstanceOf[S])
  }

  /** INTERNAL API */
  // "public" api against the impl/testkit
  final def _internalHandleCommand(commandName: String, command: Any, context: CommandContext): CommandResult = {
    val commandEffect =
      try {
        entity._internalSetCommandContext(Optional.of(context))
        entity._internalSetCurrentState(stateOrEmpty())
        handleCommand(commandName, stateOrEmpty(), command, context)
          .asInstanceOf[KeyValueEntityEffectImpl[Any]]
      } catch {
        case CommandHandlerNotFound(name) =>
          throw new EntityExceptions.EntityException(
            context.entityId(),
            context.commandId(),
            commandName,
            s"No command handler found for command [$name] on ${entity.getClass}")
      } finally {
        entity._internalSetCommandContext(Optional.empty())
      }

    if (!commandEffect.hasError()) {
      commandEffect.primaryEffect match {
        case UpdateState(newState) =>
          if (newState == null)
            throw new IllegalArgumentException("updateState with null state is not allowed.")
          state = Some(newState.asInstanceOf[S])
        case DeleteEntity => state = None
        case _            =>
      }
    }

    CommandResult(commandEffect)
  }

  protected def handleCommand(
      commandName: String,
      state: S,
      command: Any,
      context: CommandContext): KeyValueEntity.Effect[_]

  def entityClass: Class[_] = entity.getClass
}
