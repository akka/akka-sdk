/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.javasdk.annotations.FunctionTool
import akka.javasdk.annotations.Param
import akka.javasdk.impl.reflection.Reflect.Syntax.AnnotatedElementOps
import akka.javasdk.impl.reflection.Reflect.Syntax.MethodOps
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiJsonSchema

// TODO: find a better place for this
object FunctionDescriptors {

  def apply(any: Any): Seq[SpiAgent.FunctionDescriptor] =
    apply(any.getClass)

  def apply(cls: Class[_]): Seq[SpiAgent.FunctionDescriptor] = {

    cls.getDeclaredMethods
      .filter(m => m.hasAnnotation[FunctionTool] && m.isPublic)
      .map { method =>

        val toolAnno = method.getAnnotation(classOf[FunctionTool])

        val name =
          if (toolAnno.name() == null || toolAnno.name().trim.isEmpty) method.getName
          else toolAnno.name()

        val properties = method.getParameters.map { param =>
          val description = param.annotationOption[Param].map(_.description)
          param.getType match {
            case t if t == classOf[String] =>
              (param.getName, new SpiJsonSchema.JsonSchemaString(description))
            case t if t == classOf[Int] =>
              (param.getName, new SpiJsonSchema.JsonSchemaInteger(description))
            case t if t == classOf[Long] =>
              (param.getName, new SpiJsonSchema.JsonSchemaNumber(description))
            case t if t == classOf[Double] =>
              (param.getName, new SpiJsonSchema.JsonSchemaNumber(description))
            case t if t == classOf[Boolean] =>
              (param.getName, new SpiJsonSchema.JsonSchemaBoolean(description))
            case _ =>
              // FIXME: support arrays and objects
              throw new IllegalArgumentException(s"Unsupported parameter type: ${param.getType}")
          }
        }.toMap

        val objSchema = new SpiJsonSchema.JsonSchemaObject(
          description = None,
          properties = properties,
          required = properties.keySet.toSeq)

        new SpiAgent.FunctionDescriptor(name, toolAnno.description(), schema = objSchema)

      }
      .toSeq
  }
}
