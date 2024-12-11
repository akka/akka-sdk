/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.annotation.InternalApi
import akka.runtime.sdk.spi.views.SpiType
import akka.runtime.sdk.spi.views.SpiType.SpiBoolean
import akka.runtime.sdk.spi.views.SpiType.SpiByteString
import akka.runtime.sdk.spi.views.SpiType.SpiDouble
import akka.runtime.sdk.spi.views.SpiType.SpiFloat
import akka.runtime.sdk.spi.views.SpiType.SpiInteger
import akka.runtime.sdk.spi.views.SpiType.SpiLong
import akka.runtime.sdk.spi.views.SpiType.SpiNestableType
import akka.runtime.sdk.spi.views.SpiType.SpiString
import akka.runtime.sdk.spi.views.SpiType.SpiTimestamp

import java.lang.reflect.AccessFlag
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Optional

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
    classOf[java.time.Instant] -> SpiTimestamp)

  def apply(javaType: Type): SpiType =
    typeNameMap.get(javaType.getTypeName) match {
      case Some(found) => found
      case None =>
        val clazz = javaType match {
          case c: Class[_]          => c
          case p: ParameterizedType => p.getRawType.asInstanceOf[Class[_]]
        }
        knownConcreteClasses.get(clazz) match {
          case Some(found) => found
          case None        =>
            // trickier ones where we have to look at type parameters etc
            if (clazz.isArray && clazz.componentType() == classOf[java.lang.Byte]) {
              SpiByteString
            } else if (clazz.isEnum) {
              new SpiType.SpiEnum(clazz.getName)
            } else {
              javaType match {
                case p: ParameterizedType if clazz == classOf[Optional[_]] =>
                  new SpiType.SpiOptional(apply(p.getActualTypeArguments.head).asInstanceOf[SpiNestableType])
                case p: ParameterizedType if classOf[java.util.Collection[_]].isAssignableFrom(clazz) =>
                  new SpiType.SpiList(apply(p.getActualTypeArguments.head).asInstanceOf[SpiNestableType])
                case _: Class[_] =>
                  new SpiType.SpiClass(
                    clazz.getName,
                    clazz.getDeclaredFields
                      .filterNot(f => f.accessFlags().contains(AccessFlag.STATIC))
                      // FIXME recursive classes with fields of their own type
                      .filterNot(_.getType == clazz)
                      .map(field => new SpiType.SpiField(field.getName, apply(field.getGenericType)))
                      .toSeq)
              }
            }
        }
    }

}
