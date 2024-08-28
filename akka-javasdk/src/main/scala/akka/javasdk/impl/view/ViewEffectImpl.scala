/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.javasdk.view.TableUpdater

object ViewEffectImpl {
  sealed trait PrimaryEffect[S] extends TableUpdater.Effect[S]
  case class Update[S](state: S) extends PrimaryEffect[S]
  case object Delete extends PrimaryEffect[Any]
  case object Ignore extends PrimaryEffect[Any]

  private val _builder = new TableUpdater.Effect.Builder[Any] {
    override def updateRow(newState: Any): TableUpdater.Effect[Any] = Update(newState)
    override def deleteRow(): TableUpdater.Effect[Any] = Delete.asInstanceOf[PrimaryEffect[Any]]
    override def ignore(): TableUpdater.Effect[Any] = Ignore.asInstanceOf[PrimaryEffect[Any]]
  }
  def builder[S](): TableUpdater.Effect.Builder[S] =
    _builder.asInstanceOf[TableUpdater.Effect.Builder[S]]
}
