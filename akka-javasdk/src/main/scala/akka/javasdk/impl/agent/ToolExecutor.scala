/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.Optional

import scala.jdk.CollectionConverters.ListHasAsScala

import akka.annotation.InternalApi
import akka.javasdk.agent.MessageContent
import akka.javasdk.impl.agent.FunctionTools.FunctionToolInvoker
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.SpiAgent

/**
 * INTERNAL API
 */
@InternalApi
class ToolExecutor(functionTools: Map[String, FunctionToolInvoker], serializer: Serializer) {

  /**
   * Executes a tool call command synchronously, returning its result as text.
   */
  def execute(request: SpiAgent.ToolCallCommand): String = {
    val (toolInvoker, toolResult) = invoke(request)
    textResult(toolInvoker, toolResult)
  }

  /**
   * Executes a tool call command synchronously, returning its result as a sequence of message contents.
   */
  def executeMultimodal(request: SpiAgent.ToolCallCommand): Seq[SpiAgent.MessageContent] = {
    val (toolInvoker, toolResult) = invoke(request)
    if (isListOfMessageContent(toolInvoker.genericReturnType)) {
      if (toolResult == null)
        throw new IllegalArgumentException(
          s"Tool [${request.name}] declares a List<${classOf[MessageContent].getSimpleName}> return type but returned null. " +
          "A multimodal tool must return a non-null list with at least one non-null MessageContent.")
      val contents = toolResult
        .asInstanceOf[java.util.List[MessageContent]]
        .asScala
        .collect { case msg if msg != null => AgentImpl.toSpiToolResultContent(msg) }
        .toSeq
      if (contents.isEmpty)
        throw new IllegalArgumentException(
          s"Tool [${request.name}] declares a List<${classOf[MessageContent].getSimpleName}> return type but returned no content. " +
          "A multimodal tool must return a non-null list with at least one non-null MessageContent.")
      contents
    } else if (classOf[MessageContent].isAssignableFrom(toolInvoker.returnType)) {
      if (toolResult == null)
        throw new IllegalArgumentException(
          s"Tool [${request.name}] declares a ${classOf[MessageContent].getSimpleName} return type but returned null. " +
          "A multimodal tool must return a non-null MessageContent.")
      Seq(AgentImpl.toSpiToolResultContent(toolResult.asInstanceOf[MessageContent]))
    } else
      Seq(new SpiAgent.TextMessageContent(textResult(toolInvoker, toolResult)))
  }

  private def isListOfMessageContent(tpe: Type): Boolean = tpe match {
    case pt: ParameterizedType if pt.getRawType == classOf[java.util.List[_]] =>
      pt.getActualTypeArguments match {
        case Array(arg) => elementClass(arg).exists(classOf[MessageContent].isAssignableFrom)
        case _          => false
      }
    case _ => false
  }

  /**
   * Resolve the element type to a raw class so that `List<MessageContent>`, a subtype `List<ImageMessageContent>`, a
   * bounded wildcard `List<? extends MessageContent>`, and a bounded type variable `<T extends MessageContent>` are all
   * recognized as content lists. Unbounded element types (e.g. `List<?>`) resolve to `Object` and fall through to JSON.
   */
  private def elementClass(tpe: Type): Option[Class[_]] = tpe match {
    case c: Class[_]         => Some(c)
    case wt: WildcardType    => wt.getUpperBounds.collectFirst { case c: Class[_] => c }
    case tv: TypeVariable[_] => tv.getBounds.collectFirst { case c: Class[_] => c }
    case _                   => None
  }

  /** Resolve the tool invoker, deserialize the JSON arguments, and invoke the tool. */
  private def invoke(request: SpiAgent.ToolCallCommand): (FunctionToolInvoker, Any) = {

    val toolInvoker =
      functionTools.getOrElse(request.name, throw new IllegalArgumentException(s"Unknown tool ${request.name}"))

    val mapper = serializer.objectMapper
    val jsonNode = mapper.readTree(request.arguments)

    val methodInput =
      toolInvoker.paramNames.zipWithIndex.map { case (name, index) =>
        // assume that the paramName in the method matches a node from the json 'content'
        val node = jsonNode.get(name)
        val typ = toolInvoker.types(index)
        val javaType = mapper.getTypeFactory.constructType(typ)
        val deserialized = mapper.treeToValue(node, javaType).asInstanceOf[Any]

        if (deserialized == null && classOf[Optional[_]].isAssignableFrom(javaType.getRawClass)) {
          Optional.ofNullable(deserialized)
        } else {
          deserialized
        }
      }

    (toolInvoker, toolInvoker.invoke(methodInput))
  }

  private def textResult(toolInvoker: FunctionToolInvoker, toolResult: Any): String =
    if (toolInvoker.returnType == Void.TYPE)
      "SUCCESS"
    else if (toolInvoker.returnType == classOf[String])
      toolResult.asInstanceOf[String]
    else
      serializer.objectMapper.writeValueAsString(toolResult)

}
