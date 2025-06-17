/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.DependencyProvider
import akka.javasdk.agent.Agent
import akka.javasdk.annotations.FunctionTool
import akka.javasdk.impl.JsonSchema
import akka.javasdk.impl.reflection.Reflect.Syntax.AnnotatedElementOps
import akka.javasdk.impl.reflection.Reflect.Syntax.MethodOps
import akka.runtime.sdk.spi.SpiAgent

import java.lang.reflect.Method
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

  def descriptorsForAgent[A <: Agent](cls: Class[A]): Seq[SpiAgent.ToolDescriptor] =
    toToolDescriptors(cls, allowNonPublic = true)

  def descriptorsFor(cls: Class[_]): Seq[SpiAgent.ToolDescriptor] = {

    // we only validate against non-agent classes,
    // the Agent class is added by default and is not required to have a method annotated with FunctionTool
    if (annotatedMethods(cls, allowNonPublic = false).isEmpty)
      throw new IllegalArgumentException(s"No tools found in class [${cls.getName}]")

    toToolDescriptors(cls, allowNonPublic = false)
  }

  def toolInvokersForAgent[A <: Agent](anyAgent: A): Map[String, FunctionToolInvoker] =
    collectFunctionToolInvokers(anyAgent.getClass, allowNonPublic = true) { () => anyAgent }

  def toolInvokersFor(any: Any): Map[String, FunctionToolInvoker] =
    collectFunctionToolInvokers(any.getClass, allowNonPublic = false) { () => any }

  def toolInvokersFor(
      cls: Class[_],
      dependencyProvider: Option[DependencyProvider]): Map[String, FunctionToolInvoker] = {
    collectFunctionToolInvokers(cls, allowNonPublic = false) { () =>
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

    annotatedMethods(cls, allowNonPublic).map { method =>

      val name = toolName(method)

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
    }.toMap
  }

  private def toolName(method: Method): String = {
    val toolAnno = method.getAnnotation(classOf[FunctionTool])

    if (toolAnno.name() == null || toolAnno.name().isBlank) method.getName
    else toolAnno.name()

  }

  private def toToolDescriptors(cls: Class[_], allowNonPublic: Boolean): Seq[SpiAgent.ToolDescriptor] = {

    annotatedMethods(cls, allowNonPublic).map { method =>

      val toolAnno = method.getAnnotation(classOf[FunctionTool])
      val objSchema = JsonSchema.jsonSchemaFor(method)

      val name = toolName(method)
      new SpiAgent.ToolDescriptor(name, toolAnno.description(), schema = objSchema)

    }
  }

  /**
   * Collects all methods annotated with `@FunctionTool` from the given class, including inherited methods.
   */
  private def annotatedMethods(cls: Class[_], allowNonPublic: Boolean): Seq[Method] = {

    def allMethods(c: Class[_]): Seq[Method] = {
      if (c == null || c == classOf[Object]) Seq.empty
      else c.getDeclaredMethods.toSeq ++ allMethods(c.getSuperclass) ++ c.getInterfaces.flatMap(allMethods)
    }

    allMethods(cls)
      .filter(m => m.hasAnnotation[FunctionTool])
      .filter(m => m.isPublic || allowNonPublic)
      .distinct
  }
}
