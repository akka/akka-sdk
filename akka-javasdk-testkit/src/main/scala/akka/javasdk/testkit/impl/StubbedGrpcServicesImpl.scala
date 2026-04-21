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
import akka.javasdk.testkit.StubbedGrpcServices

/**
 * INTERNAL API
 *
 * @param initial
 *   Keyed on service name -> (client class -> stub instance), matching the testkit settings shape.
 */
@InternalApi
final class StubbedGrpcServicesImpl(
    initial: java.util.Map[String, java.util.Map[Class[_ <: AkkaGrpcClient], AkkaGrpcClient]])
    extends StubbedGrpcServices {

  private val initialEntries: Map[ClientKey, AkkaGrpcClient] =
    initial.asScala.iterator.flatMap { case (serviceName, byClass) =>
      byClass.asScala.iterator.map { case (cls, instance) =>
        ClientKey(cls, serviceName) -> instance
      }
    }.toMap

  private val stubs = new ConcurrentHashMap[ClientKey, AkkaGrpcClient]()
  initialEntries.foreach { case (k, v) => stubs.put(k, v) }

  def lookup(key: ClientKey): Optional[AkkaGrpcClient] =
    Optional.ofNullable(stubs.get(key))

  override def stubResponse[T <: AkkaGrpcClient](serviceName: String, serviceClass: Class[T], stubInstance: T): Unit =
    stubs.put(ClientKey(serviceClass, serviceName), stubInstance)

  override def remove[T <: AkkaGrpcClient](serviceName: String, serviceClass: Class[T]): Unit = {
    stubs.remove(ClientKey(serviceClass, serviceName))
    ()
  }

  override def reset(): Unit = {
    stubs.clear()
    initialEntries.foreach { case (k, v) => stubs.put(k, v) }
  }
}
