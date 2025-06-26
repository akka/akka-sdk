/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.javasdk.Context
import akka.javasdk.impl.InternalContext
import akka.javasdk.testkit.MockRegistry

import scala.jdk.OptionConverters.RichOptional

class AbstractTestKitContext(mockRegistry: MockRegistry) extends Context with InternalContext {

  def getComponentGrpcClient[T](serviceClass: Class[T]): T =
    mockRegistry
      .asInstanceOf[MockRegistryImpl]
      .get(serviceClass)
      .toScala
      .getOrElse(throw new NoSuchElementException(
        s"Could not find mock for component of type $serviceClass. Hint: use ${classOf[MockRegistry].getName} to provide an instance when testing services calling other components."))

  override def selfRegion(): String = ""
}
