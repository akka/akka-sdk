/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.grpc.javadsl.AkkaGrpcClient
import akka.javasdk.impl.grpc.GrpcClientProviderImpl.ClientKey
import akka.javasdk.testkit.MockedGrpcServices

/**
 * INTERNAL API
 *
 * @param initial
 *   Keyed on service name -> (client class -> mock instance), matching the testkit settings shape.
 */
@InternalApi
final class MockedGrpcServicesImpl(
    initial: java.util.Map[String, java.util.Map[Class[_ <: AkkaGrpcClient], AkkaGrpcClient]])
    extends MockedGrpcServices {

  private val initialEntries: Map[ClientKey, AkkaGrpcClient] =
    initial.asScala.iterator.flatMap { case (serviceName, byClass) =>
      byClass.asScala.iterator.map { case (cls, instance) =>
        ClientKey(cls, serviceName) -> instance
      }
    }.toMap

  private val mocks = new ConcurrentHashMap[ClientKey, AkkaGrpcClient]()
  initialEntries.foreach { case (k, v) => mocks.put(k, v) }

  def lookup(key: ClientKey): Optional[AkkaGrpcClient] =
    Optional.ofNullable(mocks.get(key))

  override def mockResponse[T <: AkkaGrpcClient](serviceName: String, serviceClass: Class[T], mockInstance: T): Unit =
    mocks.put(ClientKey(serviceClass, serviceName), mockInstance)

  override def remove[T <: AkkaGrpcClient](serviceName: String, serviceClass: Class[T]): Unit = {
    mocks.remove(ClientKey(serviceClass, serviceName))
    ()
  }

  override def reset(): Unit = {
    mocks.clear()
    initialEntries.foreach { case (k, v) => mocks.put(k, v) }
  }
}
