/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Optional

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.http.javadsl.model.HttpEntity
import akka.http.javadsl.model.HttpRequest
import akka.javasdk.annotations.Description
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaArray
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaBoolean
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaDataType
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaEnum
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaInteger
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaNumber
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaObject
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaString
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition
import com.google.protobuf.Descriptors
import com.google.protobuf.GeneratedMessageV3
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object JsonSchema {

  private val log = LoggerFactory.getLogger(getClass)

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

  def jsonSchemaFor(tpe: Type): JsonSchemaDataType = {
    jsonSchemaTypeFor(javaTypeFor(tpe), None, Set.empty)._1
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
    val (schemaType, required) = jsonSchemaTypeFor(javaTypeFor(genericType), description, Set.empty)
    TypedParameter(parameter.getName, genericType, schemaType, required)
  }

  private def string(description: Option[String]) = new JsonSchemaString(description)
  private def number(description: Option[String]) = new JsonSchemaNumber(description)
  private def integer(description: Option[String]) = new JsonSchemaInteger(description)
  private def boolean(description: Option[String]) = new JsonSchemaBoolean(description)

  private final val typeNameMap: Map[String, Option[String] => JsonSchemaDataType] = Map(
    "short" -> integer,
    "byte" -> integer,
    "char" -> string, // Jackson writes a char as a one character string
    "int" -> integer,
    "long" -> integer,
    "double" -> number,
    "float" -> number,
    "boolean" -> boolean,
    "java.lang.Short" -> integer,
    "java.lang.Byte" -> integer,
    "java.lang.Character" -> string,
    "java.lang.Integer" -> integer,
    "java.lang.Long" -> integer,
    "java.lang.Double" -> number,
    "java.lang.Float" -> number,
    "java.lang.Boolean" -> boolean,
    "java.math.BigDecimal" -> number)

  private final val typesWithStringJsonRepresentation = Set[Class[_]](
    classOf[String], // obviously
    classOf[java.time.Instant], // time and date types rendered as string in json
    classOf[java.time.LocalTime],
    classOf[java.time.LocalDate],
    classOf[java.time.LocalDateTime],
    classOf[java.time.ZonedDateTime],
    classOf[java.time.OffsetDateTime],
    classOf[java.time.OffsetTime],
    classOf[java.time.Duration], // example Jackson output "PT72H"
    classOf[java.time.Period],
    classOf[java.util.UUID])

  private def emptyObject(description: Option[String]) =
    new JsonSchemaObject(description, properties = Map.empty, required = Seq.empty)

  private def javaTypeFor(tpe: Type): JavaType =
    JsonSerializer.internalObjectMapper.getTypeFactory.constructType(tpe)

  // Note: we don't support recursive types, not quite sure if we should though
  private def jsonSchemaTypeFor(
      javaType: JavaType,
      description: Option[String],
      seenTypes: Set[Class[_]]): (JsonSchemaDataType, Boolean) = {
    val clazz = javaType.getRawClass
    typeNameMap.get(clazz.getName) match {
      case Some(jsTypeFactory) => (jsTypeFactory(description), true)
      case None =>
        try {
          if (clazz == classOf[HttpRequest]) // for body parameter
            (emptyObject(description), false) // unknown if body is required
          else if (clazz == classOf[HttpEntity.Strict]) // for body parameter
            (emptyObject(description), true)
          else if (seenTypes.contains(clazz))
            (emptyObject(description), true) // avoid infinite recursion
          else if (typesWithStringJsonRepresentation.contains(clazz))
            (new JsonSchemaString(description), true)
          else if (javaType.isArrayType)
            (new JsonSchemaArray(jsonSchemaTypeFor(javaType.getContentType, None, seenTypes)._1, description), true)
          else if (clazz == classOf[Optional[_]]) {
            val (jsonFieldType, _) = jsonSchemaTypeFor(javaType.containedTypeOrUnknown(0), description, seenTypes)
            (jsonFieldType, false)
          } else if (javaType.isCollectionLikeType)
            (new JsonSchemaArray(jsonSchemaTypeFor(javaType.getContentType, None, seenTypes)._1, description), true)
          else if (javaType.isMapLikeType)
            (emptyObject(description), true)
          else if (classOf[GeneratedMessageV3].isAssignableFrom(clazz)) {
            val descriptor = Reflect.protoDescriptorFor(clazz.asSubclass(classOf[GeneratedMessageV3]))
            (protobufJsonSchemaFromDescriptor(descriptor, description), true)
          } else if (clazz.isEnum) {
            val values = clazz.getEnumConstants.toIndexedSeq.map(_.asInstanceOf[Enum[_]].name)
            (new JsonSchemaEnum(values, description), true)
          } else {
            val properties = jacksonProperties(javaType).map { property =>
              val description = Option(property.getPrimaryMember.getAnnotation(classOf[Description])).map(_.value())
              property.getName -> jsonSchemaTypeFor(property.getAccessor.getType, description, seenTypes + clazz)
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
            log.debug("Failed to generate schema for [{}], returning 'object'", javaType, ex)
            (emptyObject(description), true)
        }
    }
  }

  // The properties Jackson writes for a type. Introspected with the SDK mapper configuration, so naming strategies,
  // ignores, renames, getters and annotations inherited from superclasses or interfaces apply exactly as in
  // serialization. The accessor types are resolved against the type, so type variables of generic types are bound.
  private def jacksonProperties(javaType: JavaType): Seq[BeanPropertyDefinition] = {
    val config = JsonSerializer.internalObjectMapper.getSerializationConfig
    val beanDescription = config.introspect(javaType)
    val ignoredNames = Option(config.getDefaultPropertyIgnorals(javaType.getRawClass, beanDescription.getClassInfo))
      .map(_.findIgnoredForSerialization.asScala.toSet)
      .getOrElse(Set.empty[String])

    beanDescription.findProperties.asScala.toVector
      .filter(property => property.couldSerialize && !ignoredNames(property.getName))
  }

  private def protobufFieldToJsonSchema(field: Descriptors.FieldDescriptor): JsonSchemaDataType = {
    import Descriptors.FieldDescriptor.JavaType
    val baseType = field.getJavaType match {
      case JavaType.STRING      => new JsonSchemaString(None)
      case JavaType.INT         => new JsonSchemaInteger(None)
      case JavaType.LONG        => new JsonSchemaInteger(None)
      case JavaType.DOUBLE      => new JsonSchemaNumber(None)
      case JavaType.FLOAT       => new JsonSchemaNumber(None)
      case JavaType.BOOLEAN     => new JsonSchemaBoolean(None)
      case JavaType.BYTE_STRING => new JsonSchemaString(None) // base64-encoded in JSON
      case JavaType.ENUM        =>
        // protobuf JSON uses string enum names
        val values = field.getEnumType.getValues.asScala.map(_.getName).toSeq
        new JsonSchemaEnum(values, None)
      case JavaType.MESSAGE => protobufJsonSchemaFromDescriptor(field.getMessageType, None)
    }
    if (field.isRepeated) new JsonSchemaArray(baseType, None)
    else baseType
  }

  private def protobufJsonSchemaFromDescriptor(
      descriptor: Descriptors.Descriptor,
      description: Option[String]): JsonSchemaObject = {
    val fields = descriptor.getFields.asScala.toVector
    val properties = fields.map { field =>
      field.getName -> protobufFieldToJsonSchema(field)
    }.toMap
    // All proto3 fields are always present (they have defaults)
    new JsonSchemaObject(description = description, properties = properties, required = fields.map(_.getName).sorted)
  }

}
