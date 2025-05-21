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
import akka.javasdk.annotations.mcp.McpToolParameterDescription
import akka.javasdk.annotations.mcp.ToolAnnotation
import akka.javasdk.impl.Mcp.BlobResourceContents
import akka.javasdk.impl.Mcp.Implementation
import akka.javasdk.impl.Mcp.TextResourceContents
import akka.parboiled2.util.Base64
import akka.runtime.sdk.spi.HttpEndpointDescriptor
import org.slf4j.LoggerFactory

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Optional
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * INTERNAL API
 */
@InternalApi
object McpEndpointDescriptorFactory {

  private final val log = LoggerFactory.getLogger(classOf[McpEndpointDescriptorFactory.type])

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
          if (method.getParameterCount == 0) Mcp.InputSchema(properties = Map.empty, required = Seq.empty)
          else inputSchemaFor(method.getParameterTypes.head)
        } else {
          JsonRpc.Serialization.mapper.readValue(annotation.inputSchema(), classOf[Mcp.InputSchema])
        }

      val toolName =
        if (annotation.name().isBlank) method.getName
        else annotation.name()

      val toolAnnotations = toolAnnotationsFor(mcpEndpointClass, method.getName, annotation.annotations().toVector)

      val toolDescription = Mcp.ToolDescription(toolName, annotation.description(), inputSchema, toolAnnotations)

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

    val toolsByName = tools.groupBy { case (desc, _) => desc.name }
    val duplicateTools = toolsByName.collect { case (name, tools) if tools.size > 1 => name }.toSeq
    if (duplicateTools.nonEmpty) {
      throw ValidationException(
        s"MCP Tools with duplicated names exist in ${mcpEndpointClass.getName}: [${duplicateTools.mkString(", ")}], make sure that each tool has a unique name")
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
            val base64Encoded = Base64.rfc2045().encodeToString(bytes, lineSep = false)
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

    val resourcesByUri = resources.groupBy { case (res, _) => res.uri }
    val duplicateUris = resourcesByUri.collect { case (name, resources) if resources.size > 1 => name }
    if (duplicateUris.nonEmpty) {
      throw ValidationException(
        s"MCP resources with duplicated names exist in ${mcpEndpointClass.getName}: [${duplicateUris.mkString(", ")}], make sure that each resource has a separate, unique URI")
    }

    new Mcp.StatelessMcpEndpoint(
      Mcp.McpDescriptor(
        implementation =
          Implementation(name = endpointAnnotation.serverName(), version = endpointAnnotation.serverVersion()),
        resources = resources,
        resourceTemplates = Seq.empty,
        tools = tools,
        instructions = endpointAnnotation.instructions()))
      .httpEndpoint(endpointAnnotation.path())
  }

  private[impl] def inputSchemaFor(value: Class[_]): Mcp.InputSchema = {
    val properties = value.getDeclaredFields.toSeq.map { field =>
      val description = field.getAnnotation(classOf[McpToolParameterDescription]) match {
        case null =>
          log.info(
            "Field [{}] is missing a tool description, client LLMs may not understand the purpose of the field, add one using {}",
            classOf[McpToolParameterDescription].getName,
            field.getName)
          ""
        case annotation =>
          annotation.value()
      }

      val (jsonFieldType, optional) = jsonSchemaTypeFor(field.getGenericType)
      field.getName -> (Mcp.ToolProperty(`type` = jsonFieldType, description = description), optional)
    }.toMap

    Mcp.InputSchema(
      properties = properties.map { case (key, (toolProperty, _)) => key -> toolProperty },
      required = properties.collect { case (key, (_, optional)) if !optional => key }.toSeq)
  }

  private final val typeNameMap = Map(
    "short" -> "number",
    "byte" -> "number",
    "char" -> "number",
    "int" -> "number",
    "long" -> "number",
    "double" -> "number",
    "float" -> "number",
    "boolean" -> "boolean",
    "java.lang.Short" -> "number",
    "java.lang.Byte" -> "number",
    "java.lang.Char" -> "number",
    "java.lang.Integer" -> "number",
    "java.lang.Long" -> "number",
    "java.lang.Double" -> "number",
    "java.lang.Float" -> "number",
    "java.lang.Boolean" -> "boolean")

  private def jsonSchemaTypeFor(genericFieldType: Type): (String, Boolean) = {
    typeNameMap.get(genericFieldType.getTypeName) match {
      case Some(jsTypeName) => (jsTypeName, false)
      case None =>
        val clazz = genericFieldType match {
          case c: Class[_]          => c
          case p: ParameterizedType => p.getRawType.asInstanceOf[Class[_]]
        }
        if (clazz == classOf[String]) ("string", false)
        else {
          genericFieldType match {
            case p: ParameterizedType if clazz == classOf[Optional[_]] =>
              val (jsonFieldType, _) = jsonSchemaTypeFor(p.getActualTypeArguments.head)
              (jsonFieldType, true)
            case other =>
              // FIXME support collections
              // FIXME support nested classes
              throw new IllegalArgumentException(s"Unsupported field type for MCP tool input: $other")
          }
        }
    }
  }

  private def toolAnnotationsFor(
      endpointClass: Class[?],
      methodName: String,
      annotations: Seq[ToolAnnotation]): Option[Mcp.ToolAnnotation] = {
    if (annotations.isEmpty) None
    else {
      val set = annotations.toSet
      def annotationPresent(trueAnnotation: ToolAnnotation, falseAnnotation: ToolAnnotation): Option[Boolean] = {
        if (set.contains(trueAnnotation) && set.contains(falseAnnotation))
          throw new IllegalArgumentException(
            s"Tool method $methodName on ${endpointClass.getName} has both of opposite tool annotations [$trueAnnotation, $falseAnnotation], chose one.")
        else if (set.contains(trueAnnotation)) Some(true)
        else if (set.contains(falseAnnotation)) Some(false)
        else None
      }

      Some(
        Mcp.ToolAnnotation(
          destructive = annotationPresent(ToolAnnotation.Destructive, ToolAnnotation.NonDestructive),
          idempotent = annotationPresent(ToolAnnotation.Idempotent, ToolAnnotation.NonIdempotent),
          openWorld = annotationPresent(ToolAnnotation.OpenWorld, ToolAnnotation.ClosedWorld),
          readOnly = annotationPresent(ToolAnnotation.ReadOnly, ToolAnnotation.Mutating)))
    }
  }
}
