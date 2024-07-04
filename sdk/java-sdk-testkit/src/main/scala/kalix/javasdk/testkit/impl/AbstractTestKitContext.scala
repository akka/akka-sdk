/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit.impl

import akka.stream.Materializer
import akka.platform.javasdk.Context
import akka.platform.javasdk.impl.InternalContext
import akka.platform.javasdk.testkit.MockRegistry

import scala.jdk.OptionConverters.RichOptional

class AbstractTestKitContext(mockRegistry: MockRegistry) extends Context with InternalContext {

  override def materializer(): Materializer = throw new UnsupportedOperationException(
    "Accessing the materializer from testkit not supported yet")

  def getComponentGrpcClient[T](serviceClass: Class[T]): T =
    mockRegistry
      .asInstanceOf[MockRegistryImpl]
      .get(serviceClass)
      .toScala
      .getOrElse(throw new NoSuchElementException(
        s"Could not find mock for component of type $serviceClass. Hint: use ${classOf[MockRegistry].getName} to provide an instance when testing services calling other components."))

}
