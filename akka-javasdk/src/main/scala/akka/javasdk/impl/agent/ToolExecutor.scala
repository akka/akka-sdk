/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.Optional

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
   *
   * A tool that declares a [[MessageContent]] return type produces multimodal content (text, an image/PDF URI
   * reference, or inline image/PDF bytes) sent back to the model. Any other return type keeps the existing behaviour:
   * text as-is, or JSON serialization, wrapped as a single text content.
   */
  def executeMultimodal(request: SpiAgent.ToolCallCommand): Seq[SpiAgent.MessageContent] = {
    val (toolInvoker, toolResult) = invoke(request)
    // Dispatch on the declared return type, not the runtime value: a MessageContent-returning tool is
    // multimodal; anything else is text/JSON. This keeps empty and element-type handling unambiguous.
    if (classOf[MessageContent].isAssignableFrom(toolInvoker.returnType)) {
      if (toolResult == null)
        throw new IllegalArgumentException(
          s"Tool [${request.name}] declares a ${classOf[MessageContent].getSimpleName} return type but returned null. " +
          "A multimodal tool must return a non-null MessageContent.")
      Seq(AgentImpl.toSpiToolResultContent(toolResult.asInstanceOf[MessageContent]))
    } else
      Seq(new SpiAgent.TextMessageContent(textResult(toolInvoker, toolResult)))
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
