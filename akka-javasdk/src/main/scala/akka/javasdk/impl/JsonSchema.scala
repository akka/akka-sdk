/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.annotation.Annotation
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Optional
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.OptionalLong

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
import com.fasterxml.jackson.databind.introspect.AnnotatedField
import com.fasterxml.jackson.databind.introspect.AnnotatedMember
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor
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
    jsonSchemaTypeFor(tpe, None, Set.empty)._1
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
    "java.lang.String" -> string,
    "java.math.BigDecimal" -> number)

  // Jackson reports these as arrays, but writes them as a string, base64 for bytes
  private final val arrayTypesWithStringJsonRepresentation =
    Set[Class[_]](classOf[Array[Byte]], classOf[Array[Char]], classOf[java.nio.ByteBuffer])

  // The schema for a class that Jackson writes as a string, number or boolean, such as URI, BigInteger or Date
  private def scalarSchemaFor(clazz: Class[_]): Option[Option[String] => JsonSchemaDataType] = {
    var schema: Option[Option[String] => JsonSchemaDataType] = None
    val visitor = new JsonFormatVisitorWrapper.Base {
      override def expectStringFormat(tpe: JavaType): JsonStringFormatVisitor = { schema = Some(string); null }
      override def expectNumberFormat(tpe: JavaType): JsonNumberFormatVisitor = { schema = Some(number); null }
      override def expectIntegerFormat(tpe: JavaType): JsonIntegerFormatVisitor = { schema = Some(integer); null }
      override def expectBooleanFormat(tpe: JavaType): JsonBooleanFormatVisitor = { schema = Some(boolean); null }
    }
    try {
      JsonSerializer.internalObjectMapper.acceptJsonFormatVisitor(clazz, visitor)
      schema
    } catch {
      case NonFatal(_) => None
    }
  }

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
            case _ if arrayTypesWithStringJsonRepresentation.contains(clazz) =>
              (string(description), true)
            case _ if clazz == classOf[OptionalInt] || clazz == classOf[OptionalLong] =>
              (integer(description), false)
            case _ if clazz == classOf[OptionalDouble] =>
              (number(description), false)
            case _ if clazz.isArray =>
              (new JsonSchemaArray(jsonSchemaTypeFor(clazz.getComponentType, None, seenTypes)._1, None), true)
            case g: GenericArrayType =>
              (new JsonSchemaArray(jsonSchemaTypeFor(g.getGenericComponentType, None, seenTypes)._1, description), true)
            case p: ParameterizedType if clazz == classOf[Optional[_]] =>
              val (jsonFieldType, _) = jsonSchemaTypeFor(p.getActualTypeArguments.head, description, seenTypes)
              (jsonFieldType, false)
            case p: ParameterizedType if classOf[java.util.Collection[_]].isAssignableFrom(clazz) =>
              (
                new JsonSchemaArray(
                  items = jsonSchemaTypeFor(p.getActualTypeArguments.head, None, seenTypes)._1,
                  description),
                true)
            case _ if classOf[GeneratedMessageV3].isAssignableFrom(clazz) =>
              val descriptor = Reflect.protoDescriptorFor(clazz.asSubclass(classOf[GeneratedMessageV3]))
              (protobufJsonSchemaFromDescriptor(descriptor, description), true)

            case _ if clazz.isEnum =>
              val values = clazz.getEnumConstants.toIndexedSeq.map(_.asInstanceOf[Enum[_]].name)
              (new JsonSchemaEnum(values, description), true)

            case _ =>
              scalarSchemaFor(clazz) match {
                case Some(scalarSchema) => (scalarSchema(description), true)
                case None               => (objectSchemaFor(clazz, description, seenTypes), true)
              }
          }
        } catch {
          case NonFatal(ex) =>
            log.debug("Failed to generate schema for [{}], returning 'object'", genericFieldType, ex)
            (emptyObject(description), true)
        }
    }
  }

  // Note: for now the top level can only be a class
  private def objectSchemaFor(
      clazz: Class[_],
      description: Option[String],
      seenTypes: Set[Class[_]]): JsonSchemaObject = {
    val properties = jacksonProperties(clazz).map { case (name, propertyType, member) =>
      val description = Option(member.getAnnotation(classOf[Description])).map(_.value())
      name -> jsonSchemaTypeFor(propertyType, description, seenTypes + clazz)
    }.toMap

    new JsonSchemaObject(
      description = description,
      properties = properties.map { case (key, (schemaType, _)) => key -> schemaType },
      // All fields that are not wrapped in Optional are listed as required
      required = properties.collect { case (key, (_, required)) if required => key }.toSeq.sorted)
  }

  // The properties Jackson writes for a class, by JSON name, with the generic type and the member carrying the
  // annotations. Introspected with the SDK mapper configuration, so naming strategies, ignores, renames, getters
  // and annotations inherited from superclasses or interfaces apply exactly as in serialization.
  private def jacksonProperties(clazz: Class[_]): Seq[(String, Type, AnnotatedMember)] = {
    val config = JsonSerializer.internalObjectMapper.getSerializationConfig
    val beanDescription = config.introspect(config.constructType(clazz))
    val ignoredNames = Option(config.getDefaultPropertyIgnorals(clazz, beanDescription.getClassInfo))
      .map(_.findIgnoredForSerialization.asScala.toSet)
      .getOrElse(Set.empty[String])

    beanDescription.findProperties.asScala.toVector
      .filter(property => property.couldSerialize && !ignoredNames(property.getName))
      .map { property =>
        val propertyType = property.getAccessor match {
          case field: AnnotatedField   => field.getAnnotated.getGenericType
          case getter: AnnotatedMethod => getter.getAnnotated.getGenericReturnType
        }
        (property.getName, propertyType, property.getPrimaryMember)
      }
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
