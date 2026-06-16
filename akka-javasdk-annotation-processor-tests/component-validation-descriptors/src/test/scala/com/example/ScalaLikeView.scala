/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import akka.javasdk.annotations.{ Component, Consume, Query }
import akka.javasdk.view.{ TableUpdater, View }

/**
 * Scala-idiomatic View fixture: the TableUpdater lives in the companion object, not in the primary class. At the JVM
 * level `Updater` ends up in `ScalaLikeView$.getDeclaredClasses()`, not in `ScalaLikeView.getDeclaredClasses()`.
 *
 * RuntimeTypeDef.getNestedTypes() must search the companion class to find it.
 */
object ScalaLikeView {
  case class Row(id: String, value: String)

  @Consume.FromTopic("test-topic")
  class Updater extends TableUpdater[Row] {
    def onEvent(r: Row): TableUpdater.Effect[Row] = effects().updateRow(r)
  }
}

@Component(id = "scala-like-view")
class ScalaLikeView extends View {
  @Query("SELECT * FROM scala_rows")
  def getAll(): View.QueryEffect[ScalaLikeView.Row] = queryResult()
}
