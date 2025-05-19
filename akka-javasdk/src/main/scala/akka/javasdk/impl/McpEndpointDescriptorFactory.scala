/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.http.scaladsl.model.MediaTypes
import akka.javasdk.annotations.mcp.McpEndpoint
import akka.javasdk.annotations.mcp.McpResource
import akka.javasdk.annotations.mcp.McpTool
import akka.javasdk.impl.Mcp.BlobResourceContents
import akka.javasdk.impl.Mcp.Implementation
import akka.javasdk.impl.Mcp.TextResourceContents
import akka.parboiled2.util.Base64
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
      if (method.getParameterCount > 1)
        throw new IllegalArgumentException(
          s"MCP tool methods must accept 0 or 1 parameters, but ${method.getName} accepts ${method.getParameterCount}")

      val inputSchema =
        if (annotation.inputSchema().isBlank) {
          // FIXME reflectively infer schema from type
          //   what do we support, only object, or also individual parameters (and use their names)?
          ???
        } else {
          JsonRpc.Serialization.mapper.readValue(annotation.inputSchema(), classOf[Mcp.InputSchema])
        }

      val toolDescription = Mcp.ToolDescription(annotation.name(), annotation.description(), inputSchema, None)

      val callback = (params: Map[String, Any]) =>
        Future[Mcp.CallToolResult] {
          val endpointInstance = instanceFactory.apply()
          val returnValue = if (method.getParameterCount == 0) {
            // FIXME fail on input params when expecting none
            method.invoke(endpointInstance)
          } else {
            // FIXME fail on no input (can we know with manually specified schema?)
            // FIXME handle required fields and input validation correctly
            val parsedInput = JsonRpc.Serialization.mapper.convertValue(params, method.getParameterTypes.head)
            method.invoke(endpointInstance, parsedInput)
          }
          returnValue match {
            case text: String => Mcp.CallToolResult(Seq(Mcp.TextContent(text)))
            case unknown      =>
              // FIXME cover with validation
              // FIXME handle/allow audio and image
              throw new RuntimeException(
                s"Unsupported tool return value (${if (unknown == null) "null" else unknown.getClass.toString}")
          }

        }

      toolDescription -> callback
    }

    val resourceMethods = allMethods.flatMap { method =>
      val resourceAnnotation = method.getAnnotation(classOf[McpResource])
      if (resourceAnnotation != null) {
        Some((resourceAnnotation, method))
      } else None
    }

    val resources = resourceMethods.map { case (annotation, method) =>
      if (method.getParameterCount > 0)
        throw new IllegalArgumentException(
          s"MCP resources must be of 0 arity, but ${method.getName} has a non empty parameter list")

      val resourceDescription = Mcp.Resource(
        uri = annotation.uri(),
        name = annotation.name(),
        description = Some(annotation.description()).filter(!_.isBlank),
        mimeType = Some(annotation.mimeType()).filter(!_.isBlank),
        annotations = None,
        size = None)

      val callback = { () =>
        val endpointInstance = instanceFactory()
        val result = method.invoke(endpointInstance)
        result match {
          case bytes: Array[Byte] =>
            val base64Encoded = Base64.rfc2045().encodeToString(bytes, false)
            Seq(
              BlobResourceContents(
                base64Encoded,
                resourceDescription.uri,
                resourceDescription.mimeType.getOrElse(MediaTypes.`application/octet-stream`.value)))
          case text: String =>
            Seq(
              TextResourceContents(
                text,
                resourceDescription.uri,
                mimeType = resourceDescription.mimeType.getOrElse(MediaTypes.`text/plain`.value)))
        }
      }

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
