/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.impl.reflection.ParameterExtractor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

import scala.util.control.Exception.Catcher

import akka.javasdk.impl.serialization.JsonSerializer
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.google.protobuf.Descriptors

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class CommandHandler(
    grpcMethodName: String,
    serializer: JsonSerializer,
    requestMessageDescriptor: Descriptors.Descriptor,
    methodInvokers: Map[String, MethodInvoker]) {

  /**
   * This method will look up for a registered method that receives a super type of the incoming payload. It's only
   * called when a direct method is not found.
   *
   * The incoming typeUrl is for one of the existing sub types, but the method itself is defined to receive a super
   * type. Therefore we look up the method parameter to find out if one of its sub types matches the incoming typeUrl.
   */
  private def lookupMethodAcceptingSubType(inputTypeUrl: String): Option[MethodInvoker] = {
    methodInvokers.values.find { javaMethod =>
      //None could happen if the method is a delete handler
      val lastParam = javaMethod.method.getParameterTypes.lastOption
      if (lastParam.exists(_.getAnnotation(classOf[JsonSubTypes]) != null)) {
        lastParam.get.getAnnotation(classOf[JsonSubTypes]).value().exists { subType =>
          inputTypeUrl == serializer
            .contentTypeFor(subType.value()) //TODO requires more changes to be used with JsonMigration
        }
      } else false
    }
  }

  def isSingleNameInvoker: Boolean = methodInvokers.size == 1

  def lookupInvoker(inputTypeUrl: String): Option[MethodInvoker] =
    methodInvokers
      .get(serializer.removeVersion(inputTypeUrl))
      .orElse(lookupMethodAcceptingSubType(inputTypeUrl))

  def getInvoker(inputTypeUrl: String): MethodInvoker =
    lookupInvoker(inputTypeUrl).getOrElse {
      throw new NoSuchElementException(
        s"Couldn't find any entry for typeUrl [$inputTypeUrl] in [${methodInvokers.view.mapValues(_.method.getName).mkString}].")
    }

  // for embedded SDK we expect components to be either zero or one arity
  def getSingleNameInvoker(): MethodInvoker =
    if (methodInvokers.size != 1) throw new IllegalStateException(s"More than one method defined for $grpcMethodName")
    else methodInvokers.head._2
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object MethodInvoker {

  def apply(javaMethod: Method, parameterExtractor: ParameterExtractor[InvocationContext, AnyRef]): MethodInvoker =
    MethodInvoker(javaMethod, Array(parameterExtractor))

}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class MethodInvoker(
    method: Method,
    parameterExtractors: Array[ParameterExtractor[InvocationContext, AnyRef]]) {

  /**
   * To invoke methods with parameters an InvocationContext is necessary extract them from the message.
   */
  def invoke(componentInstance: AnyRef, invocationContext: InvocationContext): AnyRef = {
    try method.invoke(componentInstance, parameterExtractors.map(e => e.extract(invocationContext)): _*)
    catch unwrapInvocationTargetException()
  }

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
