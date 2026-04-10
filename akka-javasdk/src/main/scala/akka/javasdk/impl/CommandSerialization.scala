/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util

import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.BytesPayload

/**
 * INTERNAL API
 */
@InternalApi
object CommandSerialization {

  // Content type used by autonomous agent delegation for tool call arguments
  private val UntypedJsonObjectContentType = JsonSerializer.JsonContentTypePrefix + "object"

  def deserializeComponentClientCommand(
      method: Method,
      command: BytesPayload,
      serializer: Serializer): Option[AnyRef] = {
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
            // When the payload is an untyped JSON object (e.g. from autonomous agent delegation where
            // the LLM produces tool call arguments as a JSON object wrapping the method parameter),
            // unwrap the single property value to match the expected parameter type.
            if (command.contentType == UntypedJsonObjectContentType) {
              Some(unwrapSingleParameter(method, command, paramClass, serializer))
            } else {
              Some(serializer.fromBytes(paramClass, command).asInstanceOf[AnyRef])
            }
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
                serializer.json.fromBytes(
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
            s"Could not deserialize message of type [${command.contentType}] to type [${parameterTypes.head.getTypeName}] " +
            s"as expected by method [${method.getDeclaringClass.getName}.${method.getName}]",
            ex)
      }
    }
  }

  /**
   * Unwraps a single property from a JSON object payload, matching the ToolExecutor pattern. The JSON from LLM tool
   * calls is always an object like {"paramName": value}, where paramName matches the method parameter name.
   */
  private def unwrapSingleParameter(
      method: Method,
      command: BytesPayload,
      paramClass: Class[_],
      serializer: Serializer): AnyRef = {
    val mapper = serializer.objectMapper
    val jsonNode = mapper.readTree(command.bytes.toArrayUnsafe())
    val paramName = method.getParameters.head.getName
    val valueNode = jsonNode.get(paramName)
    if (valueNode == null) {
      throw new IllegalArgumentException(
        s"JSON object does not contain expected property [$paramName] " +
        s"for method [${method.getDeclaringClass.getName}.${method.getName}]")
    }
    val javaType = mapper.getTypeFactory.constructType(paramClass)
    mapper.treeToValue(valueNode, javaType).asInstanceOf[AnyRef]
  }
}
