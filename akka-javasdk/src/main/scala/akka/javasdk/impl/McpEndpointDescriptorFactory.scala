/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.Optional

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.OptionConverters.RichOption
import scala.reflect.ClassTag
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.Uri
import akka.javasdk.annotations.Acl
import akka.javasdk.annotations.Description
import akka.javasdk.annotations.JWT
import akka.javasdk.annotations.mcp.McpEndpoint
import akka.javasdk.annotations.mcp.McpPrompt
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
import akka.runtime.sdk.spi.McpEndpointDescriptor.PromptArgument
import akka.runtime.sdk.spi.McpEndpointDescriptor.PromptMessage
import akka.runtime.sdk.spi.McpEndpointDescriptor.PromptMethodDescriptor
import akka.runtime.sdk.spi.McpEndpointDescriptor.PromptResult
import akka.runtime.sdk.spi.McpEndpointDescriptor.Resource
import akka.runtime.sdk.spi.McpEndpointDescriptor.ResourceMethodDescriptor
import akka.runtime.sdk.spi.McpEndpointDescriptor.ResourceTemplateMethodDescriptor
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
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaObject
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaString
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
object McpEndpointDescriptorFactory {

  private val logger = LoggerFactory.getLogger("akka.javasdk.mcp")

  private lazy val objectMapper = {
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

  private val UriTemplateVariable = """\{([^}]+)}""".r

  def apply[T](mcpEndpointClass: Class[T], instanceFactory: McpEndpointConstructionContext => T)(implicit
      sdkExecutor: ExecutionContext): McpEndpointDescriptor = {

    val endpointAnnotation = mcpEndpointClass.getAnnotation(classOf[McpEndpoint])

    val toolMethods = methodsWithAnnotation[McpTool](mcpEndpointClass)
    val tools = toolMethods
      .map { case (annotation, method) =>
        if (method.getReturnType != classOf[String])
          throw new IllegalArgumentException(
            s"MCP tool method must return String, but [${mcpEndpointClass.getName}.${method.getName}] returns [${method.getReturnType}]")

        val inputSchema: JsonSchemaObject =
          if (annotation.inputSchema().isBlank) {
            if (method.getParameterCount == 0)
              new JsonSchemaObject(properties = Map.empty, required = Seq.empty, description = None)
            else JsonSchema.jsonSchemaFor(method)
          } else {
            objectMapper.readValue(annotation.inputSchema(), classOf[JsonSchemaObject])
          }

        val toolName =
          if (annotation.name().isBlank) method.getName
          else annotation.name()

        val toolAnnotations = toolAnnotationsFor(mcpEndpointClass, method.getName, annotation.annotations().toVector)
        val toolDescription = new ToolDescription(toolName, annotation.description(), inputSchema, toolAnnotations)

        val requiredParameterNames = inputSchema.required.toSet
        val callback = (context: McpEndpointConstructionContext, params: Map[String, Any]) =>
          Future[ResponseContent] {
            val endpointInstance = instanceFactory.apply(context)
            val returnValue = if (method.getParameterCount == 0) {
              method.invoke(endpointInstance)
            } else {
              val parsedParams = method.getParameters.map { param =>
                val required = requiredParameterNames(param.getName)
                params.get(param.getName) match {
                  case Some(unparsedValue) =>
                    val paramValue = objectMapper.convertValue(unparsedValue, param.getType)
                    if (required) paramValue
                    else Optional.ofNullable(paramValue)
                  case None =>
                    if (required)
                      throw new IllegalArgumentException(
                        s"Missing required tool parameter [${param.getName}] for tool [$toolName]")
                    else Optional.empty()
                }
              }
              method.invoke(endpointInstance, parsedParams: _*)
            }
            returnValue match {
              case text: String => new TextContent(text)
              case unknown      =>
                // FIXME handle/allow audio and image content types, supported by protocol, but how do we fit it in SDK API?
                throw new RuntimeException(
                  s"Unsupported tool return value for tool [$toolName] defined in [${mcpEndpointClass.getName}.${method.getName}] (${if (unknown == null) "null"
                  else unknown.getClass.toString}")
            }
          }

        new ToolMethodDescriptor(
          toolDescription = toolDescription,
          method = callback,
          methodOptions = new MethodOptions(None, None))
      }
      .sortBy(_.toolDescription.name)

    val toolsByName = tools.groupBy { _.toolDescription.name }
    val duplicateTools = toolsByName.collect { case (name, tools) if tools.size > 1 => name }.toSeq
    if (duplicateTools.nonEmpty) {
      throw ValidationException(
        s"MCP Tools with duplicated names exist in ${mcpEndpointClass.getName}: [${duplicateTools.mkString(", ")}], make sure that each tool has a unique name")
    }

    // dynamic resources
    val resourceTemplateMethods = methodsWithAnnotation[McpResource](mcpEndpointClass).filter { case (annotation, _) =>
      annotation.uri().isEmpty
    }
    val resourceTemplates =
      resourceTemplateMethods.map { case (annotation, method) =>
        if (annotation.uriTemplate().isEmpty)
          throw ValidationException(
            s"MCP resource method [${mcpEndpointClass.getName}.${method.getName}] has both 'uri' and 'uriTemplate' empty, one of them must have a value.")

        // FIXME validate parameter names match template
        method.getParameterTypes.foreach(paramType =>
          if (paramType != classOf[String])
            throw ValidationException(
              s"Resource template method parameters must be of type String, but [${mcpEndpointClass.getName}.${method.getName}] has parameter of type [${paramType.getName}]"))

        val parametersWithoutVariables = UriTemplateVariable
          .findAllMatchIn(annotation.uriTemplate())
          .foldLeft(method.getParameters.toVector.map(_.getName).toSet) { (namesLeft, matchResult) =>
            val name = matchResult.group(1)
            if (!namesLeft(name))
              throw ValidationException(
                s"Resource template method [${mcpEndpointClass.getName}.${method.getName}] has a variable name [$name] in the uriTemplate that is not present in the parameter list.")
            else namesLeft - name
          }
        if (parametersWithoutVariables.nonEmpty)
          throw ValidationException(
            s"Resource template method [${mcpEndpointClass.getName}.${method.getName}] has a parameter name(s) [${parametersWithoutVariables
              .mkString(", ")}] that are not present in the uriTemplate list.")

        val resourceTemplate = new McpEndpointDescriptor.ResourceTemplate(
          uriTemplate = annotation.uriTemplate(),
          name = annotation.name(),
          description = Option(annotation.description()).filterNot(_.isBlank),
          mimeType = Option(resourceMimeType(annotation, method)).filterNot(_.isBlank),
          annotations = None)

        val callback = (context: McpEndpointConstructionContext, uri: Uri, variables: Map[String, String]) => {
          val params = method.getParameters.toVector.map(parameter =>
            variables.getOrElse(
              parameter.getName,
              throw new IllegalArgumentException(
                s"Resource template request was missing parameter [${parameter.getName}]")))

          val instance = instanceFactory(context)
          val result = method.invoke(instance, params: _*)
          resourceResultToMcp(result, uri.toString(), resourceTemplate.mimeType)
        }

        new ResourceTemplateMethodDescriptor(
          resourceTemplate = resourceTemplate,
          methodOptions = new MethodOptions(None, None),
          method = callback)
      }

    // static resources
    val resourceMethods = methodsWithAnnotation[McpResource](mcpEndpointClass).filter { case (annotation, _) =>
      annotation.uriTemplate().isEmpty
    }
    val resources = resourceMethods
      .map { case (annotation, method) =>
        if (method.getParameterCount > 0)
          throw new IllegalArgumentException(
            s"MCP resources must be of 0 arity, but ${method.getName} has a non empty parameter list")

        val resourceDescription = new Resource(
          uri = annotation.uri(),
          name = annotation.name(),
          description = Some(annotation.description()).filter(!_.isBlank),
          mimeType = Some(resourceMimeType(annotation, method)),
          annotations = None,
          size = None)

        val callback = { (context: McpEndpointConstructionContext) =>
          try {
            val endpointInstance = instanceFactory(context)
            val result = method.invoke(endpointInstance)
            resourceResultToMcp(result, resourceDescription.uri, resourceDescription.mimeType)
          } catch {
            case NonFatal(ex) =>
              logger.warn(s"MCP resource callback for [${mcpEndpointClass.getName}.${method.getName}] failed", ex)
              throw ex
          }
        }

        new ResourceMethodDescriptor(resourceDescription, new MethodOptions(None, None), callback)
      }
      .sortBy(_.resource.name)

    val promptMethods = methodsWithAnnotation[McpPrompt](mcpEndpointClass)
    val prompts = promptMethods.map { case (annotation, method) =>
      if (method.getReturnType != classOf[String]) {
        throw new IllegalArgumentException(
          s"MCP prompt method must return String, but [${mcpEndpointClass.getName}.${method.getName}] returns [${method.getReturnType}]")
      }

      val promptName =
        if (annotation.name().isBlank) method.getName
        else annotation.name()

      val arguments = method.getParameters.toVector.map { parameter =>
        val description = Option(parameter.getAnnotation(classOf[Description])).map(_.value())
        if (parameter.getType != classOf[String] || (parameter.getType == classOf[
            Optional[_]] && parameter.getParameterizedType
            .asInstanceOf[ParameterizedType]
            .getActualTypeArguments
            .head != classOf[String])) {
          throw new IllegalArgumentException(
            s"MCP prompt method [${method.getDeclaringClass.getName}.${method.getName}] parameter [${parameter.getName}] must be of type String or Optional<String>, but is of type [${parameter.getType}]")
        }

        val required = parameter.getType != classOf[Optional[_]]
        new PromptArgument(parameter.getName, description, required = Some(required))
      }
      val role = annotation.role()
      if (role != "user" && role != "assistant")
        throw new IllegalArgumentException(
          s"MCP prompt method [${method.getDeclaringClass.getName}.${method.getName}] annotation role value must be either 'user' or 'assistant', but is [$role]")

      val callback = { (context: McpEndpointConstructionContext, arguments: Map[String, String]) =>
        try {
          val endpointInstance = instanceFactory(context)
          val paramsInOrder = method.getParameters.map { parameter =>
            if (parameter.getType != classOf[Optional[_]]) arguments(parameter.getName)
            else arguments.get(parameter.getName).toJava
          }
          val result = method.invoke(endpointInstance, paramsInOrder: _*).asInstanceOf[String]
          Future.successful(
            new PromptResult(
              description = None,
              // For now, we only support text prompts
              messages = Seq(new PromptMessage(new TextContent(result), role))))

        } catch {
          case NonFatal(ex) =>
            logger.warn(s"MCP prompt callback for [${mcpEndpointClass.getName}.${method.getName}] failed", ex)
            throw ex
        }
      }

      new PromptMethodDescriptor(
        prompt = new McpEndpointDescriptor.Prompt(
          name = promptName,
          description = Option(annotation.description()).filterNot(_.isEmpty),
          arguments = arguments),
        method = callback,
        methodOptions = new MethodOptions(None, None))
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
      resourceTemplates = resourceTemplates,
      prompts = prompts,
      tools = tools.sortBy(_.toolDescription.name),
      componentOptions = new ComponentOptions(
        deriveAclOptions(Option(mcpEndpointClass.getAnnotation(classOf[Acl]))),
        deriveJWTOptions(Option(mcpEndpointClass.getAnnotation(classOf[JWT])), mcpEndpointClass.getCanonicalName)),
      provided = false)
  }

  private def methodsWithAnnotation[T <: Annotation](clazz: Class[_])(implicit
      classTag: ClassTag[T]): Seq[(T, Method)] =
    clazz.getDeclaredMethods.toVector.flatMap { method =>
      val annotation = method.getAnnotation(classTag.runtimeClass.asInstanceOf[Class[T]])
      if (annotation != null) {
        failOnMethodAclOrJwt(method)
        Some((annotation, method))
      } else None
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

  private def failOnMethodAclOrJwt(method: Method): Unit = {
    if (method.getAnnotation(classOf[Acl]) != null)
      throw ValidationException(
        s"MCP endpoints does not support ACL annotation on methods, found on [${method.getDeclaringClass.getName}.${method.getName}]. Only class level ACL annotation supported.")
    else if (method.getAnnotation(classOf[JWT]) != null)
      throw ValidationException(
        s"MCP endpoints does not support JWT annotation on methods, found on [${method.getDeclaringClass.getName}.${method.getName}]. Only class level JWT annotation supported.")
  }

  private def resourceMimeType(resource: McpResource, method: Method) =
    if (resource.mimeType().nonEmpty) resource.mimeType()
    else {
      val returnType = method.getGenericReturnType
      if (returnType == classOf[Array[Byte]]) {
        MediaTypes.`application/octet-stream`.value
      } else if (returnType.getTypeName == "java.lang.String") {
        MediaTypes.`text/plain`.value // implicitly UTF-8 according to spec (and since nested in JSON)
      } else {
        MediaTypes.`application/json`.value
      }
    }

  private def resourceResultToMcp(result: AnyRef, uri: String, mimeType: Option[String]) =
    result match {
      case bytes: Array[Byte] =>
        val base64Encoded = Base64.rfc2045().encodeToString(bytes, lineSep = false)
        Seq(new BlobResourceContents(base64Encoded, uri, mimeType))
      case text: String =>
        Seq(new TextResourceContents(text, uri, mimeType = mimeType))
      case other =>
        val json = objectMapper.writeValueAsString(other)
        Seq(new TextResourceContents(json, uri, mimeType = mimeType))
    }
}
