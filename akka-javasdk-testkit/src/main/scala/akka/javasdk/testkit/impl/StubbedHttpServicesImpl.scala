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
import akka.javasdk.testkit.StubbedHttpServices

/**
 * INTERNAL API
 */
@InternalApi
final class StubbedHttpServicesImpl(initial: java.util.Map[String, JFunction[HttpRequest, HttpResponse]])
    extends StubbedHttpServices {

  private val initialEntries: Map[String, JFunction[HttpRequest, HttpResponse]] =
    initial.asScala.toMap
  private val stubs = new ConcurrentHashMap[String, JFunction[HttpRequest, HttpResponse]]()
  initialEntries.foreach { case (k, v) => stubs.put(k, v) }

  def lookup(serviceName: String): Optional[JFunction[HttpRequest, HttpResponse]] =
    Optional.ofNullable(stubs.get(serviceName))

  override def stubResponse(serviceName: String, handler: JFunction[HttpRequest, HttpResponse]): Unit =
    stubs.put(serviceName, handler)

  override def remove(serviceName: String): Unit = {
    stubs.remove(serviceName)
    ()
  }

  override def reset(): Unit = {
    stubs.clear()
    initialEntries.foreach { case (k, v) => stubs.put(k, v) }
  }
}
