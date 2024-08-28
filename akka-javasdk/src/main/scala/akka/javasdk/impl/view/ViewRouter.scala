/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.javasdk.view.TableUpdater
import akka.javasdk.view.UpdateContext
import java.util.Optional

abstract class ViewUpdateRouter {
  def _internalHandleUpdate(state: Option[Any], event: Any, context: UpdateContext): TableUpdater.Effect[_]
}

abstract class ViewRouter[S, V <: TableUpdater[S]](protected val updater: V) extends ViewUpdateRouter {

  /** INTERNAL API */
  override final def _internalHandleUpdate(
      state: Option[Any],
      event: Any,
      context: UpdateContext): TableUpdater.Effect[_] = {
    val stateOrEmpty: S = state match {
      case Some(preExisting) => preExisting.asInstanceOf[S]
      case None              => updater.emptyRow()
    }
    try {
      updater._internalSetUpdateContext(Optional.of(context))
      handleUpdate(context.eventName(), stateOrEmpty, event)
    } finally {
      updater._internalSetUpdateContext(Optional.empty())
    }
  }

  def handleUpdate(commandName: String, state: S, event: Any): TableUpdater.Effect[S]

}

abstract class ViewMultiTableRouter extends ViewUpdateRouter {

  /** INTERNAL API */
  override final def _internalHandleUpdate(
      state: Option[Any],
      event: Any,
      context: UpdateContext): TableUpdater.Effect[_] = {
    viewRouter(context.eventName())._internalHandleUpdate(state, event, context)
  }

  def viewRouter(eventName: String): ViewRouter[_, _]

}
