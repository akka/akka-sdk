/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.function.{ Function => JFunction }

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.javasdk.testkit.MockedHttpServices

/**
 * INTERNAL API
 */
@InternalApi
final class MockedHttpServicesImpl(initial: java.util.Map[String, JFunction[HttpRequest, HttpResponse]])
    extends MockedHttpServices {

  private val initialEntries: Map[String, JFunction[HttpRequest, HttpResponse]] =
    initial.asScala.toMap
  private val mocks = new ConcurrentHashMap[String, JFunction[HttpRequest, HttpResponse]]()
  initialEntries.foreach { case (k, v) => mocks.put(k, v) }

  def lookup(serviceName: String): Optional[JFunction[HttpRequest, HttpResponse]] =
    Optional.ofNullable(mocks.get(serviceName))

  override def mockResponse(serviceName: String, handler: JFunction[HttpRequest, HttpResponse]): Unit =
    mocks.put(serviceName, handler)

  override def remove(serviceName: String): Unit = {
    mocks.remove(serviceName)
    ()
  }

  override def reset(): Unit = {
    mocks.clear()
    initialEntries.foreach { case (k, v) => mocks.put(k, v) }
  }
}
