/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.reflect.InvocationTargetException
import java.util.UUID
import java.util.concurrent.ExecutionException

import akka.annotation.InternalApi
import org.slf4j.MDC

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object ErrorHandling {

  case class BadRequestException(msg: String) extends RuntimeException(msg)

  val CorrelationIdMdcKey = "correlationID"

  def withCorrelationId[T](block: String => T): T = {
    val correlationId = UUID.randomUUID().toString
    MDC.put(CorrelationIdMdcKey, correlationId)
    try {
      block(correlationId)
    } finally {
      MDC.remove(CorrelationIdMdcKey)
    }
  }

  def unwrapExecutionException(exc: ExecutionException): RuntimeException = {
    exc.getCause match {
      case null                => new RuntimeException(exc.getMessage)
      case e: RuntimeException => e
      case other               => new RuntimeException(other)
    }
  }

  def unwrapInvocationTargetException(exc: InvocationTargetException): RuntimeException = {
    exc.getCause match {
      case null                => new RuntimeException(exc.getMessage)
      case e: RuntimeException => e
      case other               => new RuntimeException(other)
    }
  }

}
