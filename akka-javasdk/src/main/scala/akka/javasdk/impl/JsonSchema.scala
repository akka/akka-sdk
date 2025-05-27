/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.annotations.mcp.McpToolParameterDescription
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaArray
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaBoolean
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaDataType
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaInteger
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaNumber
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaObject
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaString

import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Optional

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object JsonSchema {

  def jsonSchemaFor(value: Class[_]): JsonSchemaObject = {
    // Note: for now the top level can only be a class
    val properties = value.getDeclaredFields.toVector.map { field: Field =>
      val description = field.getAnnotation(classOf[McpToolParameterDescription]) match {
        case null       => None
        case annotation => Some(annotation.value())
      }

      field.getName -> jsonSchemaTypeFor(field.getGenericType, description)
    }.toMap

    new JsonSchemaObject(
      description = None,
      properties = properties.map { case (key, (toolProperty, _)) => key -> toolProperty },
      // All fields that are not wrapped in Optional are listed as required
      required = properties.collect { case (key, (_, required)) if required => key }.toSeq)
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

  // Note: we don't support recursive types, not quite sure if we should though
  private def jsonSchemaTypeFor(genericFieldType: Type, description: Option[String]): (JsonSchemaDataType, Boolean) = {
    typeNameMap.get(genericFieldType.getTypeName) match {
      case Some(jsTypeFactory) => (jsTypeFactory(description), true)
      case None =>
        val clazz = genericFieldType match {
          case c: Class[_]          => c
          case p: ParameterizedType => p.getRawType.asInstanceOf[Class[_]]
        }
        if (clazz == classOf[String]) (new JsonSchemaString(description), true)
        else {
          genericFieldType match {
            case p: ParameterizedType if clazz == classOf[Optional[_]] =>
              val (jsonFieldType, _) = jsonSchemaTypeFor(p.getActualTypeArguments.head, description)
              (jsonFieldType, false)
            case p: ParameterizedType if classOf[java.util.Collection[_]].isAssignableFrom(clazz) =>
              (
                new JsonSchemaArray(items = jsonSchemaTypeFor(p.getActualTypeArguments.head, None)._1, description),
                true)
            case _ =>
              (jsonSchemaFor(clazz), true)
          }
        }
    }
  }

}
