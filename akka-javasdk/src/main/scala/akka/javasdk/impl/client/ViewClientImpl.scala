/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import akka.annotation.InternalApi
import akka.http.scaladsl.model.ContentTypes
import akka.japi.function
import akka.javasdk.JsonSupport
import akka.javasdk.Metadata
import akka.javasdk.client.ComponentInvokeOnlyMethodRef
import akka.javasdk.client.ComponentInvokeOnlyMethodRef1
import akka.javasdk.client.ComponentStreamMethodRef
import akka.javasdk.client.ComponentStreamMethodRef1
import akka.javasdk.client.NoEntryFoundException
import akka.javasdk.client.ViewClient
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.MetadataImpl.toProtocol
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.view.View
import akka.runtime.sdk.spi.ViewRequest
import akka.runtime.sdk.spi.ViewType
import akka.runtime.sdk.spi.{ ViewClient => RuntimeViewClient }
import akka.util.ByteString

import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.Optional
import scala.concurrent.ExecutionContext
import scala.jdk.FutureConverters.FutureOps
import scala.util.Success

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object ViewClientImpl {

  // not quite primitive because String is in there, but primitive enough
  private val primitiveObjects: Set[Class[_]] = Set(
    classOf[java.lang.String],
    classOf[java.lang.Boolean],
    classOf[java.lang.Character],
    classOf[java.lang.Byte],
    classOf[java.lang.Short],
    classOf[java.lang.Integer],
    classOf[java.lang.Long],
    classOf[java.lang.Float],
    classOf[java.lang.Double])

  /**
   * @param queryReturnType
   *   Un-nested return type, so would be T1 for `QueryEffect[Optional[T1]]` or T2 for `QueryEffect[T2]`
   */
  private case class ViewMethodProperties(
      serviceName: String,
      method: Method,
      methodName: String,
      declaringClass: Class[_],
      queryReturnType: Class[_])
  private def validateAndExtractViewMethodProperties[R](lambda: AnyRef): ViewMethodProperties = {
    val method = MethodRefResolver.resolveMethodRef(lambda)
    ViewCallValidator.validate(method)
    // extract view id
    val declaringClass = method.getDeclaringClass
    // FIXME Surprising that this isn' the view id declaringClass.getAnnotation(classOf[ViewId]).value()
    val serviceName = declaringClass.getName
    val methodName = method.getName.capitalize
    val queryReturnType = getViewQueryReturnType(method)
    ViewMethodProperties(serviceName, method, methodName, declaringClass, queryReturnType)
  }

  private def getViewQueryReturnType(method: Method): Class[_] = {
    val actualReturnType = if (method.getReturnType == classOf[View.QueryEffect[_]]) {
      val typeParameter = method.getGenericReturnType.asInstanceOf[ParameterizedType].getActualTypeArguments.head
      typeParameter match {
        case parameterizedType: ParameterizedType if parameterizedType.getRawType == classOf[Optional[_]] =>
          parameterizedType.getActualTypeArguments.head.asInstanceOf
        case other => other
      }
    } else if (method.getReturnType == classOf[View.QueryStreamEffect[_]]) {
      // stream so type is the element in the stream
      method.getGenericReturnType.asInstanceOf[ParameterizedType].getActualTypeArguments.head
    } else {
      throw new IllegalArgumentException(s"Method ${method.getDeclaringClass}.${method.getName} is not a view query")
    }

    actualReturnType match {
      case clz: Class[_] => clz
      case _ => throw new IllegalArgumentException(s"Type parameter of return type ${method.getName} is not a class")
    }
  }

  private def encodeArgumentAsJson(method: Method, arg: Option[Any]): ByteString = arg match {
    case Some(arg) =>
      // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
      if (arg.getClass.isPrimitive || primitiveObjects.contains(arg.getClass)) {
        JsonSupport.encodeDynamicToAkkaByteString(method.getParameters.head.getName, arg.toString)
      } else if (classOf[java.util.Collection[_]].isAssignableFrom(arg.getClass)) {
        JsonSupport.encodeDynamicCollectionToAkkaByteString(
          method.getParameters.head.getName,
          arg.asInstanceOf[java.util.Collection[_]])
      } else
        JsonSupport.encodeToAkkaByteString(arg)
    case None => ByteString.emptyByteString
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class ViewClientImpl(viewClient: RuntimeViewClient, callMetadata: Option[Metadata])(implicit
    val executionContext: ExecutionContext)
    extends ViewClient {
  import ViewClientImpl._

  override def method[T, R](methodRef: function.Function[T, View.QueryEffect[R]]): ComponentInvokeOnlyMethodRef[R] =
    createMethodRefForEitherArity(methodRef)

  override def method[T, A1, R](
      methodRef: function.Function2[T, A1, View.QueryEffect[R]]): ComponentInvokeOnlyMethodRef1[A1, R] =
    createMethodRefForEitherArity(methodRef)

  private def createMethodRefForEitherArity[A1, R](lambda: AnyRef): ComponentMethodRefImpl[A1, R] = {
    val viewMethodProperties = validateAndExtractViewMethodProperties[R](lambda)
    val returnTypeOptional = Reflect.isReturnTypeOptional(viewMethodProperties.method)

    new ComponentMethodRefImpl[AnyRef, R](
      None,
      callMetadata,
      { (maybeMetadata, maybeArg) =>
        // Note: same path for 0 and 1 arg calls
        val serializedPayload = encodeArgumentAsJson(viewMethodProperties.method, maybeArg)
        DeferredCallImpl(
          maybeArg.orNull,
          maybeMetadata.getOrElse(Metadata.EMPTY).asInstanceOf[MetadataImpl],
          ViewType,
          viewMethodProperties.serviceName,
          viewMethodProperties.methodName,
          None,
          { metadata =>
            viewClient
              .query(
                new ViewRequest(
                  viewMethodProperties.serviceName,
                  viewMethodProperties.methodName,
                  ContentTypes.`application/json`,
                  serializedPayload,
                  toProtocol(metadata.asInstanceOf[MetadataImpl]).getOrElse(
                    kalix.protocol.component.Metadata.defaultInstance)))
              .map { result =>
                val deserializedReWrapped =
                  if (result.payload.isEmpty) {
                    if (returnTypeOptional) Success(Optional.empty().asInstanceOf[R])
                    else
                      throw new NoEntryFoundException(
                        s"No matching entry found when calling ${viewMethodProperties.declaringClass}.${viewMethodProperties.methodName}")
                  } else {
                    val deserialized =
                      JsonSupport.parseBytes(result.payload.toArrayUnsafe(), viewMethodProperties.queryReturnType)
                    if (returnTypeOptional) Optional.of(deserialized)
                    else deserialized
                  }

                // Note: R could be the direct type or the wrapped optional type here Optional[UserType]
                deserializedReWrapped.asInstanceOf[R]
              }
              .asJava
          })
      },
      canBeDeferred = false).asInstanceOf[ComponentMethodRefImpl[A1, R]]

  }

  override def stream[T, R](methodRef: function.Function[T, View.QueryStreamEffect[R]]): ComponentStreamMethodRef[R] = {
    val viewMethodProperties = validateAndExtractViewMethodProperties[R](methodRef)

    () =>
      viewClient
        .queryStream(
          new ViewRequest(
            viewMethodProperties.serviceName,
            viewMethodProperties.methodName,
            ContentTypes.`application/json`,
            encodeArgumentAsJson(viewMethodProperties.method, None),
            kalix.protocol.component.Metadata.defaultInstance))
        .map { viewResult =>
          // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
          JsonSupport.parseBytes[R](
            viewResult.payload.toArrayUnsafe(),
            viewMethodProperties.queryReturnType.asInstanceOf[Class[R]])
        }
        .asJava
  }

  override def stream[T, A1, R](
      methodRef: function.Function2[T, A1, View.QueryStreamEffect[R]]): ComponentStreamMethodRef1[A1, R] = {
    val viewMethodProperties = validateAndExtractViewMethodProperties[R](methodRef)

    (arg: A1) =>
      viewClient
        .queryStream(
          new ViewRequest(
            viewMethodProperties.serviceName,
            viewMethodProperties.methodName,
            ContentTypes.`application/json`,
            encodeArgumentAsJson(viewMethodProperties.method, Some(arg)),
            kalix.protocol.component.Metadata.defaultInstance))
        .map { viewResult =>
          // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
          JsonSupport.parseBytes[R](
            viewResult.payload.toArrayUnsafe(),
            viewMethodProperties.queryReturnType.asInstanceOf[Class[R]])
        }
        .asJava
  }
}