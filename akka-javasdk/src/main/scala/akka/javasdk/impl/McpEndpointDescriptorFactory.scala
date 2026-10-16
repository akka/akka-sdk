/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.annotation.Annotation
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap

import scala.collection.immutable.VectorMap
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
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
import akka.javasdk.impl.ErrorHandling.asScalaUnwrapped
import akka.javasdk.impl.ErrorHandling.unwrapInvocationTargetExceptionCatcher
import akka.javasdk.impl.JwtDescriptorFactory.deriveJWTOptions
import akka.javasdk.impl.serialization.Serializer
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
import akka.runtime.sdk.spi.McpEndpointDescriptor.TextContent
import akka.runtime.sdk.spi.McpEndpointDescriptor.TextResourceContents
import akka.runtime.sdk.spi.McpEndpointDescriptor.ToolCallResult
import akka.runtime.sdk.spi.McpEndpointDescriptor.ToolDescription
import akka.runtime.sdk.spi.McpEndpointDescriptor.ToolMethodDescriptor
import akka.runtime.sdk.spi.MethodOptions
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaArray
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaBoolean
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaDataType
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaEnum
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaInteger
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaNumber
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaObject
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaString
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.protobuf.GeneratedMessageV3
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
object McpEndpointDescriptorFactory {

  private val logger = LoggerFactory.getLogger("akka.javasdk.mcp")

  private lazy val objectMapper = {
    val m = Serializer.newObjectMapperWithDefaults()
    m.registerModule(new DefaultScalaModule())

    val module = new SimpleModule()
    module.addDeserializer(
      classOf[JsonSchemaDataType],
      { (parser, _) =>
        val node = parser.readValueAsTree[JsonNode]()
        if (node.has("type")) {
          val typeText = node.get("type").asText()
          val hasEnumArray = node.has("enum") && node.get("enum").isArray
          if (typeText == "string" && hasEnumArray) {
            val desc = Option(node.get("description")).map(_.asText())
            val values = node.get("enum").elements().asScala.toSeq.map(_.asText())
            new JsonSchemaEnum(values, desc)
          } else {
            val nodeType = typeText match {
              case "string"  => classOf[JsonSchemaString]
              case "boolean" => classOf[JsonSchemaBoolean]
              case "integer" => classOf[JsonSchemaInteger]
              case "number"  => classOf[JsonSchemaNumber]
              case "array"   => classOf[JsonSchemaArray]
              case "object"  => classOf[JsonSchemaObject]
            }
            parser.getCodec.treeToValue(node, nodeType)
          }
        } else {
          throw new JsonMappingException(parser, s"Schema is missing a type [${node.toPrettyString}]")
        }
      })
    m.registerModule(module)
    m
  }

  // Structured content must conform to the output schema, which lists Optional fields as not required but
  // cannot express null, so empty and null fields are left out. A null in a required field gives a warning.
  private lazy val toolResultMapper =
    objectMapper.copy().setDefaultPropertyInclusion(JsonInclude.Include.NON_ABSENT)

  private val UriTemplateVariable = """\{([^}]+)}""".r

  private val JavaMapTypeRef = new TypeReference[java.util.Map[String, AnyRef]] {}
  private val JavaListTypeRef = new TypeReference[java.util.List[AnyRef]] {}

  // structured content must be a JSON object, so array results are wrapped in an object with this field
  private val ArrayResultField = "result"

  // A check of structured content against an output schema, with only the parts where a value can be missing:
  // required fields, and array elements that are null. Built once per tool.
  private[impl] sealed trait ValueCheck
  private final class ObjectCheck(val required: Array[String], val nested: Array[(String, ValueCheck)])
      extends ValueCheck
  private final class ArrayCheck(val items: Option[ValueCheck]) extends ValueCheck

  // None when no value can be missing
  private[impl] def valueCheckFor(schema: JsonSchemaDataType): Option[ValueCheck] = schema match {
    case obj: JsonSchemaObject =>
      val nested = obj.properties.toArray.flatMap { case (name, propertySchema) =>
        valueCheckFor(propertySchema).map(name -> _)
      }
      if (obj.required.isEmpty && nested.isEmpty) None
      else Some(new ObjectCheck(obj.required.toArray, nested))
    case array: JsonSchemaArray => Some(new ArrayCheck(valueCheckFor(array.items)))
    case _                      => None
  }

  // rendered only when a value is missing
  private final class ValuePath(parent: ValuePath, segment: String) {
    def child(name: String) = new ValuePath(this, name)
    def element = new ValuePath(this, "[]")
    override def toString: String =
      if (parent == null) segment
      else if (segment == "[]") s"$parent$segment"
      else {
        val parentPath = parent.toString
        if (parentPath.isEmpty) segment else s"$parentPath.$segment"
      }
  }

  // Paths of the values missing from the structured content, with `[]` for array elements. Nested values are
  // Scala collections because the mapper has the Scala module.
  private[impl] def missingValues(value: Any, check: ValueCheck, rootPath: String): Seq[String] = {
    var missing: mutable.LinkedHashSet[String] = null
    def add(path: ValuePath): Unit = {
      if (missing == null) missing = mutable.LinkedHashSet.empty
      missing += path.toString
    }
    def walk(value: Any, check: ValueCheck, path: ValuePath): Unit = check match {
      case obj: ObjectCheck =>
        value match {
          case map: java.util.Map[_, _] =>
            obj.required.foreach(name => if (!map.containsKey(name)) add(path.child(name)))
            obj.nested.foreach { case (name, nestedCheck) =>
              val nestedValue = map.get(name)
              if (nestedValue != null) walk(nestedValue, nestedCheck, path.child(name))
            }
          case map: scala.collection.Map[_, _] =>
            val fields = map.asInstanceOf[scala.collection.Map[String, Any]]
            obj.required.foreach(name => if (!fields.contains(name)) add(path.child(name)))
            obj.nested.foreach { case (name, nestedCheck) =>
              val nestedValue = fields.getOrElse(name, null)
              if (nestedValue != null) walk(nestedValue, nestedCheck, path.child(name))
            }
          case _ =>
        }
      case array: ArrayCheck =>
        val elementPath = path.element
        def walkElement(element: Any): Unit =
          if (element == null) add(elementPath)
          else array.items.foreach(walk(element, _, elementPath))
        value match {
          case list: java.util.List[_]           => list.forEach(walkElement(_))
          case seq: scala.collection.Iterable[_] => seq.foreach(walkElement)
          case _                                 =>
        }
    }
    walk(value, check, new ValuePath(null, rootPath))
    if (missing == null) Nil else missing.toSeq
  }

  private sealed trait ToolOutput {
    def outputSchema: Option[JsonSchemaObject] = None
  }
  private case object VoidToolOutput extends ToolOutput
  private case object StringToolOutput extends ToolOutput
  private case object JsonTextToolOutput extends ToolOutput
  private final case class ObjectToolOutput(schema: JsonSchemaObject) extends ToolOutput {
    override def outputSchema: Option[JsonSchemaObject] = Some(schema)
  }
  private final case class ArrayToolOutput(schema: JsonSchemaObject) extends ToolOutput {
    override def outputSchema: Option[JsonSchemaObject] = Some(schema)
  }

  def apply[T](mcpEndpointClass: Class[T], instanceFactory: McpEndpointConstructionContext => T)(implicit
      sdkExecutor: ExecutionContext): McpEndpointDescriptor = {

    val endpointAnnotation = mcpEndpointClass.getAnnotation(classOf[McpEndpoint])

    val toolMethods = methodsWithAnnotation[McpTool](mcpEndpointClass)
    val tools = toolMethods
      .map { case (annotation, method) =>
        val methodLocation = s"${mcpEndpointClass.getName}.${method.getName}"
        val (isAsync, toolOutput) = toolOutputFor(method, methodLocation)

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
        val toolDescription =
          new ToolDescription(toolName, annotation.description(), inputSchema, toolAnnotations, toolOutput.outputSchema)

        val requiredParameterNames = inputSchema.required.toSet
        def invokeTool(context: McpEndpointConstructionContext, params: Map[String, Any]): AnyRef = {
          val endpointInstance = instanceFactory.apply(context)
          try {
            if (method.getParameterCount == 0) {
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
          } catch unwrapInvocationTargetExceptionCatcher
        }

        def textResult(text: String) = new ToolCallResult(Seq(new TextContent(text)), None, isError = false)
        // empty content, the runtime adds the JSON of the structured content as text content
        def structuredResult(structured: Map[String, Any]) =
          new ToolCallResult(Seq.empty, Some(structured), isError = false)
        def serialized[R](serialize: => R): R =
          try serialize
          catch {
            case NonFatal(ex) =>
              throw new RuntimeException(
                s"Failed to serialize return value of tool [$toolName] defined in [$methodLocation] to JSON",
                ex)
          }

        val valueCheck = toolOutput match {
          case ObjectToolOutput(schema) => valueCheckFor(schema)
          case ArrayToolOutput(schema)  => valueCheckFor(schema.properties(ArrayResultField))
          case _                        => None
        }
        val warnedPaths = ConcurrentHashMap.newKeySet[String]()
        def warnOnMissingValues(structured: AnyRef, rootPath: String): Unit =
          valueCheck.foreach { check =>
            missingValues(structured, check, rootPath).filter(warnedPaths.add).foreach { valuePath =>
              logger.warn(
                "Tool [{}] defined in [{}] returned structured content without a value for [{}], for example a " +
                "null field or list element. The structured content does not match the output schema. Use " +
                "Optional for a field that can be empty. This is logged once per tool and path.",
                toolName,
                methodLocation,
                valuePath)
            }
          }

        // FIXME handle/allow audio and image content types, supported by protocol, but how do we fit it in SDK API?
        def toToolCallResult(returnValue: AnyRef): ToolCallResult =
          toolOutput match {
            case VoidToolOutput => textResult("SUCCESS")
            case _ if returnValue == null =>
              throw new IllegalStateException(
                s"Tool [$toolName] defined in [$methodLocation] returned null, a tool must return a value")
            case StringToolOutput   => textResult(returnValue.asInstanceOf[String])
            case JsonTextToolOutput => textResult(serialized(toolResultMapper.writeValueAsString(returnValue)))
            case ObjectToolOutput(_) =>
              val structured = serialized(toolResultMapper.convertValue(returnValue, JavaMapTypeRef))
              warnOnMissingValues(structured, "")
              structuredResult(VectorMap.from(structured.asScala))
            case ArrayToolOutput(_) =>
              val structured = serialized(toolResultMapper.convertValue(returnValue, JavaListTypeRef))
              warnOnMissingValues(structured, ArrayResultField)
              structuredResult(VectorMap(ArrayResultField -> structured))
          }

        val callback: (McpEndpointConstructionContext, Map[String, Any]) => Future[ToolCallResult] =
          if (isAsync) { (context, params) =>
            Future(invokeTool(context, params).asInstanceOf[CompletionStage[AnyRef]])
              .flatMap { stage =>
                if (stage == null)
                  Future.failed(new IllegalStateException(
                    s"Tool [$toolName] defined in [$methodLocation] returned null instead of a CompletionStage"))
                else asScalaUnwrapped(stage)
              }
              .map(toToolCallResult)
          } else { (context, params) =>
            Future(toToolCallResult(invokeTool(context, params)))
          }

        new ToolMethodDescriptor(
          toolDescription = toolDescription,
          methodOptions = new MethodOptions(None, None),
          call = callback)
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
          val result =
            try {
              method.invoke(instance, params: _*)
            } catch unwrapInvocationTargetExceptionCatcher
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
            val result =
              try {
                method.invoke(endpointInstance)
              } catch unwrapInvocationTargetExceptionCatcher
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
          val result =
            try {
              method.invoke(endpointInstance, paramsInOrder: _*).asInstanceOf[String]
            } catch unwrapInvocationTargetExceptionCatcher
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
    // getMethods (not getDeclaredMethods) so that annotated methods inherited from a base class are
    // included; an override without the annotation opts the inherited method out.
    clazz.getMethods.toVector.flatMap { method =>
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

  /**
   * @return
   *   if the tool is async, and how the (possibly async) tool return value is turned into a tool call result
   */
  private def toolOutputFor(method: Method, methodLocation: String): (Boolean, ToolOutput) = {
    val genericReturnType = method.getGenericReturnType
    if (classOf[CompletionStage[_]].isAssignableFrom(method.getReturnType)) {
      genericReturnType match {
        case p: ParameterizedType => (true, toolOutputForType(p.getActualTypeArguments.head, methodLocation))
        case _ =>
          throw ValidationException(
            s"MCP tool method [$methodLocation] returns [$genericReturnType], async tools must return a CompletionStage with a type parameter")
      }
    } else (false, toolOutputForType(genericReturnType, methodLocation))
  }

  // Jackson cannot serialize protobuf messages, reject them anywhere in the return type at startup
  private def failOnProtobuf(tpe: Type, methodLocation: String, seen: Set[Type]): Unit =
    if (!seen(tpe)) tpe match {
      case c: Class[_] if classOf[GeneratedMessageV3].isAssignableFrom(c) =>
        throw ValidationException(
          s"MCP tool method [$methodLocation] return type contains the protobuf message [${c.getName}], protobuf return values are not supported")
      case c: Class[_] if c.isArray => failOnProtobuf(c.getComponentType, methodLocation, seen + tpe)
      case c: Class[_] if !c.isPrimitive && !c.getName.startsWith("java.") && !c.getName.startsWith("scala.") =>
        c.getDeclaredFields
          .filterNot(field => Modifier.isStatic(field.getModifiers))
          .foreach(field => failOnProtobuf(field.getGenericType, methodLocation, seen + tpe))
      case p: ParameterizedType =>
        (p.getRawType +: p.getActualTypeArguments).foreach(failOnProtobuf(_, methodLocation, seen + tpe))
      case g: GenericArrayType => failOnProtobuf(g.getGenericComponentType, methodLocation, seen + tpe)
      case _                   => // type variables, wildcards, primitives and JDK classes
    }

  private def toolOutputForType(valueType: Type, methodLocation: String): ToolOutput = {
    failOnProtobuf(valueType, methodLocation, Set.empty)
    val valueClass: Class[_] = valueType match {
      case c: Class[_]          => c
      case p: ParameterizedType => p.getRawType.asInstanceOf[Class[_]]
      case _: GenericArrayType  => classOf[Array[AnyRef]]
      case _                    => classOf[AnyRef] // type variables and wildcards
    }
    def isJdkClass = Seq("java.", "javax.", "scala.").exists(valueClass.getName.startsWith)

    val isCollection = classOf[java.util.Collection[_]].isAssignableFrom(valueClass)

    if (valueClass == Void.TYPE || valueClass == classOf[Void]) VoidToolOutput
    else if (valueClass == classOf[String]) StringToolOutput
    else if (isCollection && !valueType.isInstanceOf[ParameterizedType])
      throw ValidationException(
        s"MCP tool method [$methodLocation] returns [$valueType], a collection return type must be a parameterized java.util.Collection type such as List<T>")
    else if ((valueClass.isArray && valueClass != classOf[Array[Byte]]) || isCollection)
      ArrayToolOutput(
        new JsonSchemaObject(
          description = None,
          properties = Map(ArrayResultField -> JsonSchema.jsonSchemaFor(valueType)),
          required = Seq(ArrayResultField)))
    else if (classOf[java.util.Map[_, _]].isAssignableFrom(valueClass))
      ObjectToolOutput(new JsonSchemaObject(description = None, properties = Map.empty, required = Seq.empty))
    else if (valueClass.isPrimitive || valueClass.isInterface || valueClass.isEnum || valueClass.isArray ||
      Modifier.isAbstract(valueClass.getModifiers) || isJdkClass)
      JsonTextToolOutput
    else
      JsonSchema.jsonSchemaFor(valueType) match {
        case schema: JsonSchemaObject => ObjectToolOutput(schema)
        case _                        => JsonTextToolOutput
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
