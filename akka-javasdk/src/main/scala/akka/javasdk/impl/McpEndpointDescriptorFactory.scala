/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.annotations.Acl
import akka.javasdk.annotations.JWT
import akka.javasdk.annotations.mcp.McpEndpoint
import akka.javasdk.annotations.mcp.McpResource
import akka.javasdk.annotations.mcp.McpTool
import akka.javasdk.annotations.mcp.ToolAnnotation
import akka.javasdk.impl.AclDescriptorFactory.deriveAclOptions
import akka.javasdk.impl.JwtDescriptorFactory.deriveJWTOptions
import akka.javasdk.impl.serialization.JsonSerializer
import akka.parboiled2.util.Base64
import akka.runtime.sdk.spi.ComponentOptions
import akka.runtime.sdk.spi.McpEndpointConstructionContext
import akka.runtime.sdk.spi.McpEndpointDescriptor
import akka.runtime.sdk.spi.McpEndpointDescriptor.BlobResourceContents
import akka.runtime.sdk.spi.McpEndpointDescriptor.Implementation
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaObject
import akka.runtime.sdk.spi.McpEndpointDescriptor.Resource
import akka.runtime.sdk.spi.McpEndpointDescriptor.ResourceMethodDescriptor
import akka.runtime.sdk.spi.McpEndpointDescriptor.ResponseContent
import akka.runtime.sdk.spi.McpEndpointDescriptor.TextContent
import akka.runtime.sdk.spi.McpEndpointDescriptor.TextResourceContents
import akka.runtime.sdk.spi.McpEndpointDescriptor.ToolDescription
import akka.runtime.sdk.spi.McpEndpointDescriptor.ToolMethodDescriptor
import akka.runtime.sdk.spi.MethodOptions
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaArray
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaBoolean
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaDataType
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaInteger
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaNumber
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaString
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import java.util.Optional
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * INTERNAL API
 */
@InternalApi
object McpEndpointDescriptorFactory {

  private lazy val schemaObjectMapper = {
    val m = JsonSerializer.newObjectMapperWithDefaults()
    m.registerModule(new DefaultScalaModule())

    val module = new SimpleModule()
    module.addDeserializer(
      classOf[JsonSchemaDataType],
      { (parser, _) =>
        val node = parser.readValueAsTree[JsonNode]()
        if (node.has("type")) {
          val nodeType = node.get("type").asText() match {
            case "string"  => classOf[JsonSchemaString]
            case "boolean" => classOf[JsonSchemaBoolean]
            case "integer" => classOf[JsonSchemaInteger]
            case "number"  => classOf[JsonSchemaNumber]
            case "array"   => classOf[JsonSchemaArray]
            case "object"  => classOf[JsonSchemaObject]
          }
          parser.getCodec.treeToValue(node, nodeType)
        } else {
          throw new JsonMappingException(parser, s"Schema is missing a type [${node.toPrettyString}]")
        }
      })
    m.registerModule(module)
    m
  }

  def apply[T](mcpEndpointClass: Class[T], instanceFactory: () => T)(implicit
      system: ActorSystem[_],
      sdkExecutor: ExecutionContext): McpEndpointDescriptor = {

    val endpointAnnotation = mcpEndpointClass.getAnnotation(classOf[McpEndpoint])

    val allMethods = mcpEndpointClass.getMethods.toVector

    val toolMethods = allMethods.flatMap { method =>
      val toolAnnotation = method.getAnnotation(classOf[McpTool])
      if (toolAnnotation != null) {
        Some((toolAnnotation, method))
      } else None
    }

    val tools = toolMethods.map { case (annotation, method) =>
      val inputSchema: JsonSchemaObject =
        if (annotation.inputSchema().isBlank) {
          if (method.getParameterCount == 0)
            new JsonSchemaObject(properties = Map.empty, required = Seq.empty, description = None)
          else JsonSchema.jsonSchemaFor(method)
        } else {
          schemaObjectMapper.readValue(annotation.inputSchema(), classOf[JsonSchemaObject])
        }

      val toolName =
        if (annotation.name().isBlank) method.getName
        else annotation.name()

      val toolAnnotations = toolAnnotationsFor(mcpEndpointClass, method.getName, annotation.annotations().toVector)

      val toolDescription = new ToolDescription(toolName, annotation.description(), inputSchema, toolAnnotations)

      val callback = (_: McpEndpointConstructionContext, params: Map[String, Any]) =>
        Future[ResponseContent] {
          val endpointInstance = instanceFactory.apply()
          val returnValue = if (method.getParameterCount == 0) {
            method.invoke(endpointInstance)
          } else {
            val parsedParams = method.getParameters.map { param =>
              params.get(param.getName) match {
                case Some(unparsedValue) =>
                  // FIXME wrap with optional if needed
                  JsonSerializer.internalObjectMapper.convertValue(unparsedValue, param.getType)
                case None =>
                  Optional.empty()
              }
            }
            // FIXME fail on no input (can we know with manually specified schema?)
            // FIXME handle required fields and input validation correctly
            method.invoke(endpointInstance, parsedParams: _*)
          }
          returnValue match {
            case text: String => new TextContent(text)
            case unknown      =>
              // FIXME cover with validation
              // FIXME handle/allow audio and image
              throw new RuntimeException(
                s"Unsupported tool return value (${if (unknown == null) "null" else unknown.getClass.toString}")
          }
        }

      new ToolMethodDescriptor(
        toolDescription = toolDescription,
        method = callback,
        methodOptions = new MethodOptions(None, None))
    }

    val toolsByName = tools.groupBy { _.toolDescription.name }
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

      val resourceDescription = new Resource(
        uri = annotation.uri(),
        name = annotation.name(),
        description = Some(annotation.description()).filter(!_.isBlank),
        mimeType = Some(annotation.mimeType()).filter(!_.isBlank),
        annotations = None,
        size = None)

      val callback = { (constructionContext: McpEndpointConstructionContext) =>
        val endpointInstance = instanceFactory()
        val result = method.invoke(endpointInstance)
        result match {
          case bytes: Array[Byte] =>
            val base64Encoded = Base64.rfc2045().encodeToString(bytes, lineSep = false)
            Seq(new BlobResourceContents(base64Encoded, resourceDescription.uri, resourceDescription.mimeType))
          case text: String =>
            Seq(new TextResourceContents(text, resourceDescription.uri, mimeType = resourceDescription.mimeType))
        }
      }

      new ResourceMethodDescriptor(resourceDescription, new MethodOptions(None, None), callback)
    }

    val resourcesByUri = resources.groupBy { res => res.resource.uri }
    val duplicateUris = resourcesByUri.collect { case (name, resources) if resources.size > 1 => name }
    if (duplicateUris.nonEmpty) {
      throw ValidationException(
        s"MCP resources with duplicated names exist in ${mcpEndpointClass.getName}: [${duplicateUris.mkString(", ")}], make sure that each resource has a separate, unique URI")
    }

    new McpEndpointDescriptor(
      endpointPath = endpointAnnotation.path(),
      implementationName = mcpEndpointClass.getName,
      implementation =
        new Implementation(name = endpointAnnotation.serverName(), version = endpointAnnotation.serverVersion()),
      instructions = endpointAnnotation.instructions(),
      resources = resources,
      resourceTemplates = Seq.empty, // FIXME user API for these
      prompts = Seq.empty, // FIXME user API for these
      tools = tools,
      componentOptions = new ComponentOptions(
        deriveAclOptions(Option(mcpEndpointClass.getAnnotation(classOf[Acl]))),
        deriveJWTOptions(Option(mcpEndpointClass.getAnnotation(classOf[JWT])), mcpEndpointClass.getCanonicalName)))
  }

  private def toolAnnotationsFor(
      endpointClass: Class[?],
      methodName: String,
      annotations: Seq[ToolAnnotation]): Option[McpEndpointDescriptor.ToolAnnotation] = {
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
        new McpEndpointDescriptor.ToolAnnotation(
          destructive = annotationPresent(ToolAnnotation.Destructive, ToolAnnotation.NonDestructive),
          idempotent = annotationPresent(ToolAnnotation.Idempotent, ToolAnnotation.NonIdempotent),
          openWorld = annotationPresent(ToolAnnotation.OpenWorld, ToolAnnotation.ClosedWorld),
          readOnly = annotationPresent(ToolAnnotation.ReadOnly, ToolAnnotation.Mutating)))
    }
  }
}
