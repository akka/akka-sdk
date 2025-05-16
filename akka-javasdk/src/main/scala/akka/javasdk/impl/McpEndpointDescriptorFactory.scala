/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.annotations.mcp.McpEndpoint
import akka.javasdk.annotations.mcp.McpResource
import akka.javasdk.annotations.mcp.McpTool
import akka.javasdk.impl.Mcp.Implementation
import akka.javasdk.impl.Mcp.ResourceContents
import akka.runtime.sdk.spi.HttpEndpointDescriptor

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * INTERNAL API
 */
@InternalApi
object McpEndpointDescriptorFactory {
  def apply[T](mcpEndpointClass: Class[T], instanceFactory: () => T)(implicit
      system: ActorSystem[_],
      sdkExecutor: ExecutionContext): HttpEndpointDescriptor = {

    val endpointAnnotation = mcpEndpointClass.getAnnotation(classOf[McpEndpoint])

    val allMethods = mcpEndpointClass.getMethods.toVector

    val toolMethods = allMethods.flatMap { method =>
      val toolAnnotation = method.getAnnotation(classOf[McpTool])
      if (toolAnnotation != null) {
        Some((toolAnnotation, method))
      } else None
    }

    val tools = toolMethods.map { case (annotation, method) =>
      val toolDescription = Mcp.ToolDescription(
        annotation.name(),
        annotation.description(),
        // FIXME inspect input parameter
        Mcp.InputSchema(`type` = "object", Map.empty, Seq.empty),
        None)

      val callback = (params: Map[String, Any]) => Future[Mcp.CallToolResult] { ??? }

      toolDescription -> callback
    }

    val resourceMethods = allMethods.flatMap { method =>
      val resourceAnnotation = method.getAnnotation(classOf[McpResource])
      if (resourceAnnotation != null) {
        Some((resourceAnnotation, method))
      } else None
    }

    val resources = resourceMethods.map { case (annotation, method) =>
      val resourceDescription = Mcp.Resource(
        uri = annotation.uri(),
        name = annotation.name(),
        description = Some(annotation.description()).filter(!_.isBlank),
        mimeType = Some(annotation.mimeType()).filter(!_.isBlank),
        annotations = None,
        size = None)

      val callback = () => Seq.empty[ResourceContents]

      resourceDescription -> callback
    }

    new Mcp.StatelessMcpEndpoint(
      Mcp.McpDescriptor(
        implementation =
          Implementation(name = endpointAnnotation.serverName(), version = endpointAnnotation.serverVersion()),
        resources = resources,
        resourceTemplates = Seq.empty,
        tools = tools,
        instructions = endpointAnnotation.instructions()))
      .httpEndpoint(endpointAnnotation.value())
  }
}
