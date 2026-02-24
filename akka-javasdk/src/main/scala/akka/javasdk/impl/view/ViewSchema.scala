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

  def apply(rootType: Type): SpiType = {
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
                      protobufSchema(clazz.asInstanceOf[Class[_ <: GeneratedMessageV3]])
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

  private def protobufSchema(clazz: Class[_ <: GeneratedMessageV3]): SpiClass = {
    val descriptor = Reflect.protoDescriptorFor(clazz)
    protobufSchemaFromDescriptor(descriptor)
  }

  private def protobufFieldToSpiType(field: Descriptors.FieldDescriptor): SpiType = {
    import Descriptors.FieldDescriptor.JavaType
    field.getJavaType match {
      case JavaType.STRING      => SpiString
      case JavaType.INT         => SpiInteger
      case JavaType.LONG        => SpiLong
      case JavaType.DOUBLE      => SpiDouble
      case JavaType.FLOAT       => SpiFloat
      case JavaType.BOOLEAN     => SpiBoolean
      case JavaType.BYTE_STRING => SpiByteString
      case JavaType.ENUM        => new SpiEnum(field.getEnumType.getFullName)
      case JavaType.MESSAGE     => protobufSchemaFromDescriptor(field.getMessageType)
    }
  }

  private def protobufSchemaFromDescriptor(descriptor: Descriptors.Descriptor): SpiClass = {
    val fields = descriptor.getFields.asScala.map { field =>
      val baseType = protobufFieldToSpiType(field)
      val spiType = if (field.isRepeated) new SpiList(baseType.asInstanceOf[SpiNestableType]) else baseType
      new SpiField(field.getName, spiType)
    }.toSeq
    new SpiClass(descriptor.getFullName, fields)
  }

}
