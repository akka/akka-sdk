/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.annotations.FunctionTool
import akka.javasdk.impl.reflection.Reflect.Syntax.AnnotatedElementOps
import akka.javasdk.impl.reflection.Reflect.Syntax.MethodOps

import java.lang.reflect.Type

/**
 * INTERNAL API
 */
@InternalApi
object FunctionTools {

  /**
   * INTERNAL API
   */
  @InternalApi
  trait FunctionToolInvoker {
    def paramNames: Array[String]

    def types: Array[Type]

    def invoke(args: Array[Any]): Any

    def returnType: Class[_]
  }

  def agentFunctionToolInvokers(any: Any): Map[String, FunctionToolInvoker] =
    collectFunctionToolInvokers(any, allowNonPublic = true)

  def apply(any: Any): Map[String, FunctionToolInvoker] =
    collectFunctionToolInvokers(any, allowNonPublic = false)

  private def collectFunctionToolInvokers(any: Any, allowNonPublic: Boolean): Map[String, FunctionToolInvoker] = {

    val cls = any.getClass
    cls.getDeclaredMethods
      .filter(m => m.hasAnnotation[FunctionTool])
      .filter(m => m.isPublic || allowNonPublic)
      .map { method =>

        val toolAnno = method.getAnnotation(classOf[FunctionTool])
        val name =
          if (toolAnno.name() == null || toolAnno.name().isBlank) method.getName
          else toolAnno.name()

        name ->
        new FunctionToolInvoker {
          override def paramNames: Array[String] =
            method.getParameters.map(_.getName)

          override def types: Array[Type] =
            method.getGenericParameterTypes

          override def invoke(args: Array[Any]): Any = {
            // the filter above should filter out non-public methods in non-agent classes
            // but no need to touch the accessibility of the method if not needed
            if (allowNonPublic) method.setAccessible(true)

            method.invoke(any, args: _*)
          }

          override def returnType: Class[_] =
            method.getReturnType
        }
      }
      .toMap
  }
}
