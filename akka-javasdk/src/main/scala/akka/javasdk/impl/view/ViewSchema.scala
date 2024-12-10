/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.annotation.InternalApi
import akka.runtime.sdk.spi.views.SpiType
import akka.runtime.sdk.spi.views.SpiType.SpiBoolean
import akka.runtime.sdk.spi.views.SpiType.SpiByteString
import akka.runtime.sdk.spi.views.SpiType.SpiClass
import akka.runtime.sdk.spi.views.SpiType.SpiDouble
import akka.runtime.sdk.spi.views.SpiType.SpiFloat
import akka.runtime.sdk.spi.views.SpiType.SpiInteger
import akka.runtime.sdk.spi.views.SpiType.SpiList
import akka.runtime.sdk.spi.views.SpiType.SpiLong
import akka.runtime.sdk.spi.views.SpiType.SpiNestableType
import akka.runtime.sdk.spi.views.SpiType.SpiString

import java.lang.reflect.AccessFlag
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.time.Instant
import java.util.Optional
import scala.reflect.classTag

/**
 * INTERNAL API
 */
@InternalApi
private[view] object ViewSchema {

  def apply(javaType: Type): SpiType = {
    if (javaType == classOf[String]) {
      SpiString
    } else if (javaType == classOf[java.lang.Long] || javaType.getTypeName == "long") {
      SpiLong
    } else if (javaType == classOf[java.lang.Integer] || javaType.getTypeName == "int"
      || javaType.getTypeName == "short"
      || javaType.getTypeName == "byte"
      || javaType.getTypeName == "char") {
      SpiInteger
    } else if (javaType == classOf[java.lang.Double] || javaType.getTypeName == "double") {
      SpiDouble
    } else if (javaType == classOf[java.lang.Float] || javaType.getTypeName == "float") {
      SpiFloat
    } else if (javaType == classOf[java.lang.Boolean] || javaType.getTypeName == "boolean") {
      SpiBoolean
    } else if (javaType == Array.emptyByteArray.getClass) {
      SpiByteString
    } else if (javaType.isInstanceOf[ParameterizedType] && classOf[java.util.Collection[_]]
        .isAssignableFrom(javaType.asInstanceOf[ParameterizedType].getRawType.asInstanceOf[Class[_]])) {
      val elementType = apply(javaType.asInstanceOf[ParameterizedType].getActualTypeArguments.head) match {
        case spiNestableType: SpiNestableType => spiNestableType
        case other => throw new IllegalArgumentException(s"Element type of list is $other, not supported")
      }
      new SpiList(elementType)
    } else {
      apply(javaType.asInstanceOf[Class[_]])
    }
  }

  def apply(clazz: Class[_]): SpiType = {
    def firstTypeParam(field: Field): Class[_] = {
      // FIXME: doesn't work for Scala Seqs of primitives (Int, Long, Float, Double, Boolean)
      field.getGenericType.asInstanceOf[ParameterizedType].getActualTypeArguments.head.asInstanceOf[Class[_]]
    }
    def figureItOut(clazz: Class[_], field: Option[Field]): SpiType = {
      if (clazz == classOf[String]) SpiType.SpiString
      else if (clazz == classOf[Int]) SpiType.SpiInteger
      else if (clazz == classOf[java.lang.Integer]) SpiType.SpiInteger
      else if (clazz == classOf[Long]) SpiType.SpiLong
      else if (clazz == classOf[java.lang.Long]) SpiType.SpiLong
      else if (clazz == classOf[Float]) SpiType.SpiFloat
      else if (clazz == classOf[java.lang.Float]) SpiType.SpiFloat
      else if (clazz == classOf[Double]) SpiType.SpiDouble
      else if (clazz == classOf[java.lang.Double]) SpiType.SpiDouble
      else if (clazz == classOf[Boolean]) SpiType.SpiBoolean
      else if (clazz == classOf[java.lang.Boolean]) SpiType.SpiBoolean
      else if (clazz == classOf[Instant]) SpiType.SpiTimestamp
      else if (clazz.isArray && clazz.componentType() == classOf[java.lang.Byte]) SpiType.SpiByteString
      else if (clazz.isEnum) new SpiType.SpiEnum(clazz.getName)
      else if (clazz == classOf[Optional[_]]) {
        new SpiType.SpiOptional(figureItOut(firstTypeParam(field.get), field).asInstanceOf[SpiNestableType])
      } else if (clazz == classOf[java.util.List[_]]) {
        // FIXME support other types of collections?
        new SpiType.SpiList(figureItOut(firstTypeParam(field.get), field).asInstanceOf[SpiNestableType])
      } else {
        new SpiType.SpiClass(
          clazz.getName,
          clazz.getDeclaredFields
            .filterNot(f => f.accessFlags().contains(AccessFlag.STATIC))
            // FIXME recursive classes with fields of their own type
            .filterNot(_.getType == clazz)
            .map(field => new SpiType.SpiField(field.getName, figureItOut(field.getType, Some(field))))
            .toSeq)
      }
    }

    figureItOut(clazz, None) match {
      case spiClass: SpiClass => spiClass
      case _ =>
        throw new IllegalArgumentException(
          s"${classTag.runtimeClass} is not a class, only classes supported as top types")
    }
  }

}
