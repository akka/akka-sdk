/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.annotations.FunctionTool
import akka.javasdk.impl.JsonSchema
import akka.javasdk.impl.reflection.Reflect.Syntax.AnnotatedElementOps
import akka.javasdk.impl.reflection.Reflect.Syntax.MethodOps
import akka.runtime.sdk.spi.SpiAgent

/**
 * INTERNAL API
 */
@InternalApi
object ToolDescriptors {

  def agentToolDescriptor(cls: Class[_]): Seq[SpiAgent.ToolDescriptor] =
    toToolDescriptors(cls, allowNonPublic = true)

  def forClass(cls: Class[_]): Seq[SpiAgent.ToolDescriptor] =
    toToolDescriptors(cls, allowNonPublic = false)

  private def toToolDescriptors(cls: Class[_], allowNonPublic: Boolean): Seq[SpiAgent.ToolDescriptor] = {

    cls.getDeclaredMethods
      .filter(m => m.hasAnnotation[FunctionTool])
      .filter(m => m.isPublic || allowNonPublic)
      .map { method =>

        val toolAnno = method.getAnnotation(classOf[FunctionTool])
        val name =
          if (toolAnno.name() == null || toolAnno.name().isBlank) method.getName
          else toolAnno.name()
        val objSchema = JsonSchema.jsonSchemaFor(method)

        new SpiAgent.ToolDescriptor(name, toolAnno.description(), schema = objSchema)

      }
      .toSeq
  }
}
