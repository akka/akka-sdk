/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.DependencyProvider
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
    collectFunctionToolInvokers(any.getClass, allowNonPublic = true) { () => any }

  def forInstance(any: Any): Map[String, FunctionToolInvoker] =
    collectFunctionToolInvokers(any.getClass, allowNonPublic = false) { () => any }

  def forClass(cls: Class[_], dependencyProvider: Option[DependencyProvider]): Map[String, FunctionToolInvoker] = {
    collectFunctionToolInvokers(cls, allowNonPublic = true) { () =>
      dependencyProvider
        .map { depProv => depProv.getDependency(cls) }
        .getOrElse {
          throw new IllegalArgumentException(
            s"Could not instantiate [${cls.getName}] as no DependencyProvider was configured. " +
            "Please provide a DependencyProvider to supply dependencies. " +
            "See https://doc.akka.io/java/setup-and-dependency-injection.html#_custom_dependency_injection")
        }
    }
  }

  private def collectFunctionToolInvokers(cls: Class[_], allowNonPublic: Boolean)(
      instanceFactory: () => Any): Map[String, FunctionToolInvoker] = {

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
            if (allowNonPublic) method.setAccessible(true)
            val instance = instanceFactory()
            method.invoke(instance, args: _*)
          }

          override def returnType: Class[_] =
            method.getReturnType
        }
      }
      .toMap
  }
}
