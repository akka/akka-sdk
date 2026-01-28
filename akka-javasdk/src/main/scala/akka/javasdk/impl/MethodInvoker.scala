/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.reflect.Method

import akka.annotation.InternalApi
import akka.javasdk.impl.ErrorHandling.unwrapInvocationTargetExceptionCatcher

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
    catch unwrapInvocationTargetExceptionCatcher
  }

  /**
   * To invoke a methods with a deserialized payload
   */
  def invokeDirectly(componentInstance: AnyRef, payload: AnyRef): AnyRef = {
    try method.invoke(componentInstance, payload)
    catch unwrapInvocationTargetExceptionCatcher
  }

}
