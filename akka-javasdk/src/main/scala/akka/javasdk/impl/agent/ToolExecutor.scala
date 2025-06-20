/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.impl.agent.FunctionTools.FunctionToolInvoker
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.SpiAgent

import java.util.Optional
import scala.concurrent.ExecutionContext

/**
 * INTERNAL API
 */
@InternalApi
class ToolExecutor(functionTools: Map[String, FunctionToolInvoker], serializer: JsonSerializer) {

  /**
   * Executes a tool call command synchronously. We use this method in AgentImpl, and the execution context used is the
   * one provided by the AgentImpl which is the sdkExecutionContext. The goal is to execute the tool call in the
   * sdkExecutionContext.
   */
  def executeAsync(request: SpiAgent.ToolCallCommand)(implicit
      ex: ExecutionContext): scala.concurrent.Future[String] = {
    scala.concurrent.Future {
      execute(request)
    }
  }

  /**
   * Executes a tool call command synchronously. This method is used in the ToolExecutorSpec - no need to go async
   * there.
   */
  private[javasdk] def execute(request: SpiAgent.ToolCallCommand): String = {

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

    val toolResult = toolInvoker.invoke(methodInput)

    if (toolInvoker.returnType == Void.TYPE)
      "SUCCESS"
    else if (toolInvoker.returnType == classOf[String])
      toolResult.asInstanceOf[String]
    else
      mapper.writeValueAsString(toolResult)
  }
}
