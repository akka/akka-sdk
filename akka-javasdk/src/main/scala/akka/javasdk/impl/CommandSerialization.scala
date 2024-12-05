/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.JsonSupport
import com.google.protobuf.any.Any.toJavaProto
import com.google.protobuf.any.{ Any => ScalaPbAny }
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util

import scala.util.control.NonFatal

import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.BytesPayload

/**
 * INTERNAL API
 */
@InternalApi
object CommandSerialization {

  // FIXME remove or convert ScalaPbAny => BytesPayload
  def deserializeComponentClientCommand(method: Method, command: ScalaPbAny): Option[AnyRef] = {
    // special cased component client calls, lets json commands through all the way
    val parameterTypes = method.getGenericParameterTypes
    if (parameterTypes.isEmpty) None
    else if (parameterTypes.size > 1)
      throw new IllegalStateException(
        s"Passing more than one parameter to the command handler [${method.getDeclaringClass.getName}.${method.getName}] is not supported, parameter types: [${parameterTypes.mkString}]")
    else {
      // we used to dispatch based on the type, since that is how it works in protobuf for eventing
      // but here we have a concrete command name, and can pick up the expected serialized type from there

      try {
        parameterTypes.head match {
          case paramClass: Class[_] =>
            Some(JsonSupport.decodeJson(paramClass, command).asInstanceOf[AnyRef])
          case parameterizedType: ParameterizedType =>
            if (classOf[java.util.Collection[_]]
                .isAssignableFrom(parameterizedType.getRawType.asInstanceOf[Class[_]])) {
              val elementType = parameterizedType.getActualTypeArguments.head match {
                case typeParamClass: Class[_] => typeParamClass
                case _ =>
                  throw new RuntimeException(
                    s"Command handler [${method.getDeclaringClass.getName}.${method.getName}] accepts a parameter that is a collection with a generic type inside, this is not supported.")
              }
              Some(
                JsonSupport.decodeJsonCollection(
                  elementType.asInstanceOf[Class[AnyRef]],
                  parameterizedType.getRawType.asInstanceOf[Class[util.Collection[AnyRef]]],
                  toJavaProto(command)))
            } else
              throw new RuntimeException(
                s"Command handler [${method.getDeclaringClass.getName}.${method.getName}] handler accepts a parameter that is a generic type [$parameterizedType], this is not supported.")
        }
      } catch {
        case NonFatal(ex) =>
          throw new IllegalArgumentException(
            s"Could not deserialize message for [${method.getDeclaringClass.getName}.${method.getName}]",
            ex)
      }
    }
  }

  def deserializeComponentClientCommand(
      method: Method,
      command: BytesPayload,
      serializer: JsonSerializer): Option[AnyRef] = {
    // special cased component client calls, lets json commands through all the way
    val parameterTypes = method.getGenericParameterTypes
    if (parameterTypes.isEmpty) None
    else if (parameterTypes.size > 1)
      throw new IllegalStateException(
        s"Passing more than one parameter to the command handler [${method.getDeclaringClass.getName}.${method.getName}] is not supported, parameter types: [${parameterTypes.mkString}]")
    else {
      // we used to dispatch based on the type, since that is how it works in protobuf for eventing
      // but here we have a concrete command name, and can pick up the expected serialized type from there

      try {
        parameterTypes.head match {
          case paramClass: Class[_] =>
            Some(serializer.fromBytes(paramClass, command).asInstanceOf[AnyRef])
          case parameterizedType: ParameterizedType =>
            if (classOf[java.util.Collection[_]]
                .isAssignableFrom(parameterizedType.getRawType.asInstanceOf[Class[_]])) {
              val elementType = parameterizedType.getActualTypeArguments.head match {
                case typeParamClass: Class[_] => typeParamClass
                case _ =>
                  throw new RuntimeException(
                    s"Command handler [${method.getDeclaringClass.getName}.${method.getName}] accepts a parameter that is a collection with a generic type inside, this is not supported.")
              }
              Some(
                serializer.fromBytes(
                  elementType.asInstanceOf[Class[AnyRef]],
                  parameterizedType.getRawType.asInstanceOf[Class[util.Collection[AnyRef]]],
                  command))
            } else
              throw new RuntimeException(
                s"Command handler [${method.getDeclaringClass.getName}.${method.getName}] handler accepts a parameter that is a generic type [$parameterizedType], this is not supported.")
        }
      } catch {
        case NonFatal(ex) =>
          throw new IllegalArgumentException(
            s"Could not deserialize message for [${method.getDeclaringClass.getName}.${method.getName}]",
            ex)
      }
    }
  }
}
