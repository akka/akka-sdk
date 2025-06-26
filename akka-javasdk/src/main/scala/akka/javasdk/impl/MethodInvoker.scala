/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

import scala.util.control.Exception.Catcher

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class MethodInvoker(method: Method) {

  /**
   * To invoke methods with arity zero.
   */
  def invoke(componentInstance: AnyRef): AnyRef = {
    try method.invoke(componentInstance)
    catch unwrapInvocationTargetException()
  }

  /**
   * To invoke a methods with a deserialized payload
   */
  def invokeDirectly(componentInstance: AnyRef, payload: AnyRef): AnyRef = {
    try method.invoke(componentInstance, payload)
    catch unwrapInvocationTargetException()
  }

  private def unwrapInvocationTargetException(): Catcher[AnyRef] = {
    case exc: InvocationTargetException if exc.getCause != null =>
      throw exc.getCause
  }
}
