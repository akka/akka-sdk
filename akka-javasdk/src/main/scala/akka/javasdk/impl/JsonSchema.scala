/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.http.javadsl.model.HttpEntity
import akka.http.javadsl.model.HttpRequest
import akka.javasdk.annotations.Description
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaArray
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaBoolean
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaDataType
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaInteger
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaNumber
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaObject
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaString
import org.slf4j.LoggerFactory

import java.lang.annotation.Annotation
import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Optional
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object JsonSchema {

  private val log = LoggerFactory.getLogger(getClass)

  val emptyObjectSchema = new JsonSchemaObject(None, Map.empty, Seq.empty)

  final case class TypedParameter(
      name: String,
      parameterType: Type,
      schemaType: JsonSchemaDataType,
      required: Boolean) {

    def description: Option[String] = schemaType.description

    def parameterClass: Class[_] = parameterType match {
      case c: Class[_]          => c
      case p: ParameterizedType => p.getRawType.asInstanceOf[Class[_]]
    }
  }

  def jsonSchemaFor(method: Method): JsonSchemaObject = {
    val parameters = typedParametersFor(method)
    new JsonSchemaObject(
      description = None,
      properties = parameters.map { case TypedParameter(name, _, schemaType, _) => name -> schemaType }.toMap,
      required = parameters.collect { case TypedParameter(name, _, _, true) => name }.sorted)
  }

  def jsonSchemaWitId(method: Method, idFieldName: String, idDescription: String): JsonSchemaObject = {
    val requiredFields = Seq(idFieldName)
    val objSchema = jsonSchemaFor(method)

    if (objSchema == emptyObjectSchema) {
      new JsonSchemaObject(
        description = None,
        properties = Map(idFieldName -> new JsonSchemaString(Some(idDescription))),
        required = requiredFields)
    } else {
      val (payloadArgName, payloadSchema) = objSchema.properties.head
      new JsonSchemaObject(
        description = None,
        properties = Map(idFieldName -> new JsonSchemaString(Some(idDescription)), payloadArgName -> payloadSchema),
        required = requiredFields :+ payloadArgName)
    }
  }

  def jsonSchemaFor(value: Class[_]): JsonSchemaDataType = {
    jsonSchemaTypeFor(value, None, Set.empty)._1
  }

  def typedParametersFor(method: Method): Seq[TypedParameter] = {
    val parameterAnnotations = method.getParameterAnnotations.toVector
    val genericParameterTypes = method.getGenericParameterTypes.toVector
    method.getParameters.toVector
      .zip(parameterAnnotations)
      .zip(genericParameterTypes)
      .map { case ((parameter, annotations), genericParameterType) =>
        typedParameter(parameter, annotations, genericParameterType)
      }
  }

  private def typedParameter(
      parameter: Parameter,
      annotations: Array[Annotation],
      genericType: Type): TypedParameter = {
    val description = annotations.collectFirst { case annotation: Description => annotation.value() }
    val (schemaType, required) = jsonSchemaTypeFor(genericType, description, Set.empty)
    TypedParameter(parameter.getName, genericType, schemaType, required)
  }

  private def number(description: Option[String]) = new JsonSchemaNumber(description)
  private def integer(description: Option[String]) = new JsonSchemaInteger(description)
  private def boolean(description: Option[String]) = new JsonSchemaBoolean(description)

  private final val typeNameMap: Map[String, Option[String] => JsonSchemaDataType] = Map(
    "short" -> integer,
    "byte" -> integer,
    "char" -> integer,
    "int" -> integer,
    "long" -> integer,
    "double" -> number,
    "float" -> number,
    "boolean" -> boolean,
    "java.lang.Short" -> integer,
    "java.lang.Byte" -> integer,
    "java.lang.Char" -> integer,
    "java.lang.Integer" -> integer,
    "java.lang.Long" -> integer,
    "java.lang.Double" -> number,
    "java.lang.Float" -> number,
    "java.lang.Boolean" -> boolean)

  private final val typesWithStringJsonRepresentation = Set[Class[_]](
    classOf[String], // obviously
    classOf[java.time.Instant], // time and date types rendered as string in json
    classOf[java.time.LocalTime],
    classOf[java.time.LocalDate],
    classOf[java.time.LocalDateTime],
    classOf[java.time.ZonedDateTime],
    classOf[java.time.Duration] // example Jackson output "PT72H"
  )

  private def emptyObject(description: Option[String]) =
    new JsonSchemaObject(description, properties = Map.empty, required = Seq.empty)

  // not tailrec but should never recurse very deep
  private def classFor(tpe: Type): Class[AnyRef] = tpe match {
    case c: Class[_]          => c.asInstanceOf[Class[AnyRef]]
    case p: ParameterizedType => p.getRawType.asInstanceOf[Class[AnyRef]]
    case g: GenericArrayType  => classFor(g.getGenericComponentType)
    case other                => throw new IllegalArgumentException(s"Currently unsupported type: $other")
  }

  // Note: we don't support recursive types, not quite sure if we should though
  private def jsonSchemaTypeFor(
      genericFieldType: Type,
      description: Option[String],
      seenTypes: Set[Class[_]]): (JsonSchemaDataType, Boolean) = {
    typeNameMap.get(genericFieldType.getTypeName) match {
      case Some(jsTypeFactory) => (jsTypeFactory(description), true)
      case None =>
        try {
          val clazz = classFor(genericFieldType)
          genericFieldType match {
            case _ if clazz == classOf[HttpRequest] => // for body parameter
              (emptyObject(description), false) // unknown if body is required
            case _ if clazz == classOf[HttpEntity.Strict] => // for body parameter
              (emptyObject(description), true)
            case _ if seenTypes.contains(clazz) =>
              (emptyObject(description), true) // avoid infinite recursion
            case _ if typesWithStringJsonRepresentation.contains(clazz) =>
              (new JsonSchemaString(description), true)
            case _ if clazz.isArray =>
              (new JsonSchemaArray(jsonSchemaTypeFor(clazz.getComponentType, None, seenTypes)._1, None), true)
            case p: ParameterizedType if clazz == classOf[Optional[_]] =>
              val (jsonFieldType, _) = jsonSchemaTypeFor(p.getActualTypeArguments.head, description, seenTypes)
              (jsonFieldType, false)
            case p: ParameterizedType if classOf[java.util.Collection[_]].isAssignableFrom(clazz) =>
              (
                new JsonSchemaArray(
                  items = jsonSchemaTypeFor(p.getActualTypeArguments.head, None, seenTypes)._1,
                  description),
                true)
            case _ =>
              // Note: for now the top level can only be a class
              val properties = clazz.getDeclaredFields.toVector.map { field: Field =>
                val description = field.getAnnotation(classOf[Description]) match {
                  case null       => None
                  case annotation => Some(annotation.value())
                }

                field.getName -> jsonSchemaTypeFor(field.getGenericType, description, seenTypes + clazz)
              }.toMap

              val jsObjectSchema = new JsonSchemaObject(
                description = description,
                properties = properties.map { case (key, (schemaType, _)) => key -> schemaType },
                // All fields that are not wrapped in Optional are listed as required
                required = properties.collect { case (key, (_, required)) if required => key }.toSeq.sorted)

              (jsObjectSchema, true)
          }
        } catch {
          case NonFatal(ex) =>
            log.debug("Failed to generate schema for [{}], returning 'object'", genericFieldType, ex)
            (emptyObject(description), true)
        }
    }
  }

}
