/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOption

import akka.javasdk.testkit.MockRegistry

private[akka] class MockRegistryImpl(var mocks: Map[Class[_], Any]) extends MockRegistry {

  def this(mocks: java.util.Map[Class[_], Any]) = {
    this(mocks.asScala.toMap)
  }

  override def withMock[T](clazz: Class[T], instance: T): MockRegistry = {
    mocks = mocks + (clazz -> instance)
    this
  }

  def get[T](clazz: Class[T]): java.util.Optional[T] =
    mocks
      .get(clazz)
      .map(clazz.cast)
      .toJava
}

object MockRegistryImpl {
  val empty = new MockRegistryImpl(Map.empty[Class[_], Any])
}
