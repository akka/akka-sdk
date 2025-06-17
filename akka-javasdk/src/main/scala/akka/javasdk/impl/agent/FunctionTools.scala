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
    toToolDescriptors(cls)

  def descriptorsFor(cls: Class[_]): Seq[SpiAgent.ToolDescriptor] = {

    // we only validate against non-agent classes,
    // the Agent class is added by default and is not required to have a method annotated with FunctionTool
    if (annotatedMethods(cls).isEmpty)
      throw new IllegalArgumentException(s"No tools found in class [${cls.getName}]")

    toToolDescriptors(cls)
  }

  def toolInvokersForAgent[A <: Agent](anyAgent: A): Map[String, FunctionToolInvoker] =
    collectFunctionToolInvokers(anyAgent.getClass) { () => anyAgent }

  def toolInvokersFor(any: Any): Map[String, FunctionToolInvoker] =
    collectFunctionToolInvokers(any.getClass) { () => any }

  def toolInvokersFor(
      cls: Class[_],
      dependencyProvider: Option[DependencyProvider]): Map[String, FunctionToolInvoker] = {
    collectFunctionToolInvokers(cls) { () =>
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

  private def collectFunctionToolInvokers(cls: Class[_])(
      instanceFactory: () => Any): Map[String, FunctionToolInvoker] = {

    resolvedMethodNames(cls).map { case (name, method) =>
      name ->
        new FunctionToolInvoker {
          override def paramNames: Array[String] =
            method.getParameters.map(_.getName)

          override def types: Array[Type] =
            method.getGenericParameterTypes

          override def invoke(args: Array[Any]): Any = {
            if (classOf[Agent].isAssignableFrom(cls)) method.setAccessible(true)
            val instance = instanceFactory()
            method.invoke(instance, args: _*)
          }

          override def returnType: Class[_] =
            method.getReturnType
        }
    }
  }

  private def toolName(method: Method, resolvedName: String): String = {
    val toolAnno = method.getAnnotation(classOf[FunctionTool])

    // use the resolved name if no custom name is provided
    if (toolAnno.name() == null || toolAnno.name().isBlank) resolvedName
    else toolAnno.name()
  }

  private def toToolDescriptors(cls: Class[_]): Seq[SpiAgent.ToolDescriptor] = {

    resolvedMethodNames(cls).map { case (name, method) =>
      val toolAnno = method.getAnnotation(classOf[FunctionTool])
      val objSchema = JsonSchema.jsonSchemaFor(method)

      new SpiAgent.ToolDescriptor(name, toolAnno.description(), schema = objSchema)

    }.toSeq
  }

  /**
   * Collects all methods annotated with `@FunctionTool` from the given class, including inherited methods.
   */
  private def annotatedMethods(cls: Class[_]): Seq[Method] = {

    val allowNonPublic = classOf[Agent].isAssignableFrom(cls)

    def allMethods(c: Class[_]): Seq[Method] = {
      if (c == null || c == classOf[Object]) Seq.empty
      else c.getDeclaredMethods.toSeq ++ allMethods(c.getSuperclass) ++ c.getInterfaces.flatMap(allMethods)
    }

    allMethods(cls)
      .filter(m => m.hasAnnotation[FunctionTool])
      .filter(m => m.isPublic || allowNonPublic)
      .distinct
  }

  private def resolvedMethodNames(cls: Class[_]): Map[String, Method] = {

    // methods are prefixed with the class simple name
    // note this is the real impl class, not the interface or parent class.
    def withClassName(name: String): String =
      cls.getSimpleName + "_" + name

    annotatedMethods(cls)
      .groupBy(method => withClassName(method.getName))
      .flatMap {
        case (originalName, Seq(method)) =>
          // if there is only one method with this name, we can use it directly
          Map(toolName(method, originalName) -> method)

        case (originalName, methods) =>
          // otherwise we need to create a unique name for each method based on its parameters
          methods.map { method =>
            val paramTypes = method.getParameterTypes.map(_.getSimpleName).mkString("_")
            val resolvedName = s"${originalName}_$paramTypes"
            (toolName(method, resolvedName), method)
          }.toMap
      }
  }
}
