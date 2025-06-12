/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.annotations.FunctionTool
import akka.javasdk.impl.reflection.Reflect.Syntax.AnnotatedElementOps

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

  def apply(any: Any): Map[String, FunctionToolInvoker] = {

    any.getClass.getDeclaredMethods
      .filter(m => m.hasAnnotation[FunctionTool])
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
            // TODO: only methods in Agent itself should be allowed to be private
            method.setAccessible(true)
            method.invoke(any, args: _*)
          }

          override def returnType: Class[_] =
            method.getReturnType
        }
      }
      .toMap
  }
}
