/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import java.lang.reflect.AccessFlag
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Optional

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.javasdk.impl.reflection.Reflect
import akka.runtime.sdk.spi.SpiSchema.SpiBoolean
import akka.runtime.sdk.spi.SpiSchema.SpiByteString
import akka.runtime.sdk.spi.SpiSchema.SpiClass
import akka.runtime.sdk.spi.SpiSchema.SpiClassRef
import akka.runtime.sdk.spi.SpiSchema.SpiDouble
import akka.runtime.sdk.spi.SpiSchema.SpiEnum
import akka.runtime.sdk.spi.SpiSchema.SpiField
import akka.runtime.sdk.spi.SpiSchema.SpiFloat
import akka.runtime.sdk.spi.SpiSchema.SpiInteger
import akka.runtime.sdk.spi.SpiSchema.SpiList
import akka.runtime.sdk.spi.SpiSchema.SpiLong
import akka.runtime.sdk.spi.SpiSchema.SpiNestableType
import akka.runtime.sdk.spi.SpiSchema.SpiNumeric
import akka.runtime.sdk.spi.SpiSchema.SpiOptional
import akka.runtime.sdk.spi.SpiSchema.SpiString
import akka.runtime.sdk.spi.SpiSchema.SpiTimestamp
import akka.runtime.sdk.spi.SpiSchema.SpiType
import com.google.protobuf.Descriptors
import com.google.protobuf.GeneratedMessageV3

@InternalApi
private[view] object ViewSchema {

  private final val typeNameMap = Map(
    "short" -> SpiInteger,
    "byte" -> SpiInteger,
    "char" -> SpiInteger,
    "int" -> SpiInteger,
    "long" -> SpiLong,
    "double" -> SpiDouble,
    "float" -> SpiFloat,
    "boolean" -> SpiBoolean)

  private final val knownConcreteClasses = Map[Class[_], SpiType](
    // wrapped types
    classOf[java.lang.Boolean] -> SpiBoolean,
    classOf[java.lang.Short] -> SpiInteger,
    classOf[java.lang.Byte] -> SpiInteger,
    classOf[java.lang.Character] -> SpiInteger,
    classOf[java.lang.Integer] -> SpiInteger,
    classOf[java.lang.Long] -> SpiLong,
    classOf[java.lang.Double] -> SpiDouble,
    classOf[java.lang.Float] -> SpiFloat,
    // special classes
    classOf[String] -> SpiString,
    // date/time types that can be treated as a timestamp
    // Note: intentionally not supporting timezone-less-types for now (to make it possible to add support in the future,
    // would require runtime changes)
    classOf[java.time.Instant] -> SpiTimestamp,
    classOf[java.time.ZonedDateTime] -> SpiTimestamp,
    classOf[java.math.BigDecimal] -> SpiNumeric)

  // Note: view row type field validation (unsupported type combinations like Optional<Collection>,
  // nested collections, Map, unsupported time types etc.) is handled by ViewValidations which runs
  // both at compile time (annotation processor) and at runtime (SdkRunner startup).
  /** Schema for a table row type. Protobuf fields with explicit presence are optional. */
  def apply(rootType: Type): SpiType = schemaFor(rootType, protobufPresence = true)

  /**
   * Schema for a query input or output type. The runtime projects a query result into a class or list of classes, so
   * protobuf fields are not wrapped in `SpiOptional` here.
   */
  def forQuery(rootType: Type): SpiType = schemaFor(rootType, protobufPresence = false)

  private def schemaFor(rootType: Type, protobufPresence: Boolean): SpiType = {
    // Note: not tail recursive but trees should not ever be deep enough that it is a problem
    def loop(currentType: Type, seenClasses: Set[Class[_]]): SpiType =
      typeNameMap.get(currentType.getTypeName) match {
        case Some(found) => found
        case None =>
          val clazz = currentType match {
            case c: Class[_]          => c
            case p: ParameterizedType => p.getRawType.asInstanceOf[Class[_]]
          }
          if (seenClasses.contains(clazz)) new SpiClassRef(clazz.getName)
          else
            knownConcreteClasses.get(clazz) match {
              case Some(found) => found
              case None        =>
                // trickier ones where we have to look at type parameters etc
                if (clazz.isArray && clazz.componentType() == classOf[java.lang.Byte]) {
                  SpiByteString
                } else if (clazz.isEnum) {
                  new SpiEnum(clazz.getName)
                } else {
                  currentType match {
                    case p: ParameterizedType if clazz == classOf[Optional[_]] =>
                      new SpiOptional(loop(p.getActualTypeArguments.head, seenClasses).asInstanceOf[SpiNestableType])
                    case p: ParameterizedType if classOf[java.util.Collection[_]].isAssignableFrom(clazz) =>
                      new SpiList(loop(p.getActualTypeArguments.head, seenClasses).asInstanceOf[SpiNestableType])
                    case _: Class[_] if classOf[GeneratedMessageV3].isAssignableFrom(clazz) =>
                      protobufSchema(clazz.asInstanceOf[Class[_ <: GeneratedMessageV3]], protobufPresence)
                    case _: Class[_] =>
                      val seenIncludingThis = seenClasses + clazz
                      new SpiClass(
                        clazz.getName,
                        clazz.getDeclaredFields
                          .filterNot(f => f.accessFlags().contains(AccessFlag.STATIC))
                          .map(field => new SpiField(field.getName, loop(field.getGenericType, seenIncludingThis)))
                          .toSeq)
                  }
                }
            }
      }

    loop(rootType, Set.empty)
  }

  private val ProtobufTimestampFullName = "google.protobuf.Timestamp"

  // well-known types that JsonFormat prints as a plain JSON value rather than an object
  private val ProtobufWrapperTypeFullNames = Set(
    "google.protobuf.DoubleValue",
    "google.protobuf.FloatValue",
    "google.protobuf.Int64Value",
    "google.protobuf.UInt64Value",
    "google.protobuf.Int32Value",
    "google.protobuf.UInt32Value",
    "google.protobuf.BoolValue",
    "google.protobuf.StringValue",
    "google.protobuf.BytesValue")
  private val ProtobufStringJsonTypeFullNames = Set("google.protobuf.Duration", "google.protobuf.FieldMask")
  private val ProtobufUnsupportedTypeFullNames =
    Set("google.protobuf.Struct", "google.protobuf.Value", "google.protobuf.ListValue", "google.protobuf.Any")

  private def protobufSchema(clazz: Class[_ <: GeneratedMessageV3], protobufPresence: Boolean): SpiNestableType =
    protobufMessageSchema(Reflect.protoDescriptorFor(clazz), protobufPresence, Set.empty)

  private def protobufMessageSchema(
      descriptor: Descriptors.Descriptor,
      protobufPresence: Boolean,
      seenMessages: Set[String]): SpiNestableType = {
    val fullName = descriptor.getFullName
    if (fullName == ProtobufTimestampFullName) SpiTimestamp
    else if (ProtobufWrapperTypeFullNames.contains(fullName))
      protobufFieldToSpiType(descriptor.findFieldByName("value"), protobufPresence, seenMessages)
    else if (ProtobufStringJsonTypeFullNames.contains(fullName)) SpiString
    else if (ProtobufUnsupportedTypeFullNames.contains(fullName))
      throw new IllegalArgumentException(
        s"Protobuf type [$fullName] is not supported in views, it does not have a fixed schema")
    else if (seenMessages.contains(fullName)) new SpiClassRef(fullName)
    else protobufSchemaFromDescriptor(descriptor, protobufPresence, seenMessages)
  }

  private def protobufFieldToSpiType(
      field: Descriptors.FieldDescriptor,
      protobufPresence: Boolean,
      seenMessages: Set[String]): SpiNestableType = {
    import Descriptors.FieldDescriptor.Type
    // the view JSON holds unsigned values, uint32 needs a long, uint64 above Long.MaxValue is not supported
    field.getType match {
      case Type.STRING                              => SpiString
      case Type.INT32 | Type.SINT32 | Type.SFIXED32 => SpiInteger
      case Type.UINT32 | Type.FIXED32               => SpiLong
      case Type.INT64 | Type.SINT64 | Type.SFIXED64 => SpiLong
      case Type.UINT64 | Type.FIXED64               => SpiLong
      case Type.DOUBLE                              => SpiDouble
      case Type.FLOAT                               => SpiFloat
      case Type.BOOL                                => SpiBoolean
      case Type.BYTES                               => SpiByteString
      case Type.ENUM                                => new SpiEnum(field.getEnumType.getFullName)
      case Type.MESSAGE | Type.GROUP => protobufMessageSchema(field.getMessageType, protobufPresence, seenMessages)
    }
  }

  private def protobufSchemaFromDescriptor(
      descriptor: Descriptors.Descriptor,
      protobufPresence: Boolean,
      seenMessages: Set[String]): SpiClass = {
    val seenIncludingThis = seenMessages + descriptor.getFullName
    val fields = descriptor.getFields.asScala.map { field =>
      val baseType = protobufFieldToSpiType(field, protobufPresence, seenIncludingThis)
      // fields with explicit presence (message, oneof, optional) can be unset, the table stores an unset field as null
      val spiType =
        if (field.isRepeated) new SpiList(baseType)
        else if (protobufPresence && field.hasPresence) new SpiOptional(baseType)
        else baseType
      new SpiField(field.getName, spiType)
    }.toSeq
    new SpiClass(descriptor.getFullName, fields)
  }

}
