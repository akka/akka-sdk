/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.view

import akka.javasdk.annotations.{ Component, Consume, Query }
import akka.javasdk.view.{ TableUpdater, View }

/**
 * Scala-idiomatic View (Option B): the TableUpdater lives in the companion object. In compiled Scala 2, the JVM
 * InnerClasses attribute lists `Updater` as a static nested class of the primary class (`ScalaViewWithCompanionUpdater`),
 * so it appears in `classOf[ScalaViewWithCompanionUpdater].getDeclaredClasses()` with ACC_STATIC set.
 *
 * ViewDescriptorFactory discovers it via the normal `viewClass.getDeclaredClasses` scan after the ACC_STATIC guard
 * was removed from `Reflect.isViewTableUpdater`.
 */
object ScalaViewWithCompanionUpdater {
  case class Row(id: String)

  @Consume.FromTopic("test-topic")
  class Updater extends TableUpdater[Row] {
    def onEvent(r: Row): TableUpdater.Effect[Row] = effects().updateRow(r)
  }
}

@Component(id = "scala-view-companion")
class ScalaViewWithCompanionUpdater extends View {
  @Query("SELECT * FROM scala_companion_rows")
  def getAll(): View.QueryEffect[ScalaViewWithCompanionUpdater.Row] = queryResult()
}

/**
 * Scala View (Option A): the TableUpdater is a non-static inner class of the primary class. The `Updater` appears in
 * `ScalaViewWithInnerUpdater.getDeclaredClasses()` but is not ACC_STATIC.
 *
 * ViewDescriptorFactory must accept it after the isStatic guard is removed from Reflect.isViewTableUpdater.
 *
 * Row is placed in the companion object so it is a regular top-level-ish class and not itself a non-static inner class
 * (which would complicate schema reflection).
 */
object ScalaViewWithInnerUpdater {
  case class Row(id: String)
}

@Component(id = "scala-view-inner")
class ScalaViewWithInnerUpdater extends View {

  @Consume.FromTopic("test-topic")
  class Updater extends TableUpdater[ScalaViewWithInnerUpdater.Row] {
    def onEvent(r: ScalaViewWithInnerUpdater.Row): TableUpdater.Effect[ScalaViewWithInnerUpdater.Row] =
      effects().updateRow(r)
  }

  @Query("SELECT * FROM scala_inner_rows")
  def getAll(): View.QueryEffect[ScalaViewWithInnerUpdater.Row] = queryResult()
}
