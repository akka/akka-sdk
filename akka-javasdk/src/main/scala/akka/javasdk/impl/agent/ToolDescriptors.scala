/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.annotations.FunctionTool
import akka.javasdk.impl.JsonSchema
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.reflection.Reflect.Syntax.AnnotatedElementOps
import akka.runtime.sdk.spi.SpiAgent

/**
 * INTERNAL API
 */
@InternalApi
object ToolDescriptors {

  def apply(any: Any): Seq[SpiAgent.ToolDescriptor] =
    apply(any.getClass)

  def apply(cls: Class[_]): Seq[SpiAgent.ToolDescriptor] = {

    // TODO: only methods in Agent itself should be allowed to be private
    cls.getDeclaredMethods
      .filter(m => m.hasAnnotation[FunctionTool])
      .map { method =>

        val toolAnno = method.getAnnotation(classOf[FunctionTool])
        val name =
          if (toolAnno.name() == null || toolAnno.name().isBlank) method.getName
          else toolAnno.name()
        val objSchema = JsonSchema.jsonSchemaFor(method)

        val description = Reflect.valueOrAlias(toolAnno)
        new SpiAgent.ToolDescriptor(name, description, schema = objSchema)

      }
      .toSeq
  }
}
