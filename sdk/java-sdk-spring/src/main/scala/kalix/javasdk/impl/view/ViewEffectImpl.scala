/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.view

import kalix.javasdk.view.View.Effect

object ViewEffectImpl {
  sealed trait PrimaryEffect[S] extends Effect[S]
  case class Update[S](state: S) extends PrimaryEffect[S]
  case object Delete extends PrimaryEffect[Any]
  case object Ignore extends PrimaryEffect[Any]

  private val _builder = new Effect.Builder[Any] {
    override def updateState(newState: Any): Effect[Any] = Update(newState)
    override def deleteState(): Effect[Any] = Delete.asInstanceOf[PrimaryEffect[Any]]
    override def ignore(): Effect[Any] = Ignore.asInstanceOf[PrimaryEffect[Any]]
  }
  def builder[S](): Effect.Builder[S] =
    _builder.asInstanceOf[Effect.Builder[S]]
}
