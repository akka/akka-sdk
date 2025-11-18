/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Optional

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters.FutureOps

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.japi.function
import akka.javasdk.Metadata
import akka.javasdk.client.ComponentInvokeOnlyMethodRef
import akka.javasdk.client.ComponentInvokeOnlyMethodRef1
import akka.javasdk.client.ComponentMethodRef
import akka.javasdk.client.ComponentMethodRef1
import akka.javasdk.client.NoEntryFoundException
import akka.javasdk.client.ViewClient
import akka.javasdk.client.ViewStreamMethodRef
import akka.javasdk.client.ViewStreamMethodRef1
import akka.javasdk.impl.ComponentDescriptorFactory
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.view.ViewStreamMethodRefImpl
import akka.javasdk.impl.view.ViewStreamMethodRefImpl1
import akka.javasdk.view.View
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.ViewRequest
import akka.runtime.sdk.spi.ViewType
import akka.runtime.sdk.spi.{ ViewClient => RuntimeViewClient }

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
  private[impl] case class ViewMethodProperties(
      componentId: String,
      method: Method,
      methodName: String,
      declaringClass: Class[_],
      queryReturnType: Type,
      returnTypeOptional: Boolean)

  private def validateAndExtractViewMethodProperties[R](method: Method): ViewMethodProperties = {
    ViewCallValidator.validate(method)
    // extract view id
    val declaringClass = method.getDeclaringClass
    val componentId = ComponentDescriptorFactory.readComponentIdValue(declaringClass)
    val methodName = method.getName
    val queryReturnType = getViewQueryReturnType(method)
    val queryReturnClass = queryReturnType match {
      case c: Class[_]          => c
      case p: ParameterizedType => p.getRawType.asInstanceOf[Class[_]]
    }
    val returnTypeOptional = queryReturnType match {
      case _: ParameterizedType if classOf[java.util.Optional[_]].isAssignableFrom(queryReturnClass) => true
      case _                                                                                         => false
    }
    ViewMethodProperties(componentId, method, methodName, declaringClass, queryReturnType, returnTypeOptional)
  }

  private def getViewQueryReturnType(method: Method): Type = {
    if (method.getReturnType == classOf[View.QueryEffect[_]] || method.getReturnType == classOf[
        View.QueryStreamEffect[_]]) {
      // regular or stream query effect both has the concrete type as the one type parameter
      method.getGenericReturnType.asInstanceOf[ParameterizedType].getActualTypeArguments.head
    } else {
      throw new IllegalArgumentException(s"Method ${method.getDeclaringClass}.${method.getName} is not a view query")
    }
  }

  private[impl] def encodeArgument(serializer: JsonSerializer, method: Method, arg: Option[Any]): BytesPayload =
    arg match {
      case Some(arg) =>
        // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
        if (arg.getClass.isPrimitive || primitiveObjects.contains(arg.getClass)) {
          val bytes = serializer.encodeDynamicToAkkaByteString(method.getParameters.head.getName, arg)
          new BytesPayload(bytes, JsonSerializer.JsonContentTypePrefix + "object")
        } else if (classOf[java.util.Collection[_]].isAssignableFrom(arg.getClass)) {
          val bytes = serializer.encodeDynamicCollectionToAkkaByteString(
            method.getParameters.head.getName,
            arg.asInstanceOf[java.util.Collection[_]])
          new BytesPayload(bytes, JsonSerializer.JsonContentTypePrefix + "object")
        } else {
          serializer.toBytes(arg)
        }
      case None =>
        BytesPayload.empty
    }

}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class ViewClientImpl(
    viewClient: RuntimeViewClient,
    serializer: JsonSerializer,
    // Note: not actually passed through to query
    callMetadata: Option[Metadata])(implicit val executionContext: ExecutionContext, system: ActorSystem[_])
    extends ViewClient {
  import ViewClientImpl._

  override def method[T, R](lambda: function.Function[T, View.QueryEffect[R]]): ComponentInvokeOnlyMethodRef[R] = {
    val method = MethodRefResolver.resolveMethodRef(lambda)
    createMethodRefForEitherArity(method)
  }

  override def method[T, A1, R](
      lambda: function.Function2[T, A1, View.QueryEffect[R]]): ComponentInvokeOnlyMethodRef1[A1, R] = {
    val method = MethodRefResolver.resolveMethodRef(lambda)
    createMethodRefForEitherArity(method)
  }

  def methodRefNoArg[R](method: Method): ComponentMethodRef[R] =
    createMethodRefForEitherArity[Nothing, R](method)

  def methodRefOneArg[A1, R](method: Method): ComponentMethodRef1[A1, R] =
    createMethodRefForEitherArity(method)

  private def createMethodRefForEitherArity[A1, R](method: Method): ComponentMethodRefImpl[A1, R] = {
    import MetadataImpl.toSpi
    val viewMethodProperties = validateAndExtractViewMethodProperties[R](method)

    new ComponentMethodRefImpl[AnyRef, R](
      None,
      callMetadata,
      { (maybeMetadata, maybeRetrySettings, maybeArg) =>
        // Note: same path for 0 and 1 arg calls
        val serializedPayload = encodeArgument(serializer, viewMethodProperties.method, maybeArg)

        def callView(metadata: Metadata): Future[R] = {
          viewClient
            .query(
              new ViewRequest(
                viewMethodProperties.componentId,
                viewMethodProperties.methodName,
                serializedPayload,
                toSpi(metadata)))
            .map { result =>
              if (result.payload.isEmpty) {
                if (viewMethodProperties.returnTypeOptional) Optional.empty().asInstanceOf[R]
                else
                  throw new NoEntryFoundException(
                    s"No matching entry found when calling ${viewMethodProperties.declaringClass}.${viewMethodProperties.methodName}")
              } else {
                serializer.fromBytes(viewMethodProperties.queryReturnType, result.payload)
              }
            }
        }

        DeferredCallImpl(
          maybeArg.orNull,
          maybeMetadata.getOrElse(Metadata.EMPTY).asInstanceOf[MetadataImpl],
          ViewType,
          viewMethodProperties.componentId,
          viewMethodProperties.methodName,
          None,
          { metadata =>
            maybeRetrySettings match {
              case Some(retrySettings) =>
                akka.pattern
                  .retry(retrySettings) { () =>
                    callView(metadata)
                  }
                  .asJava
              case None => callView(metadata).asJava
            }
          },
          serializer)
      },
      canBeDeferred = false).asInstanceOf[ComponentMethodRefImpl[A1, R]]

  }

  override def stream[T, R](lambda: function.Function[T, View.QueryStreamEffect[R]]): ViewStreamMethodRef[R] = {
    val method = MethodRefResolver.resolveMethodRef(lambda)
    val viewMethodProperties = validateAndExtractViewMethodProperties[R](method)
    new ViewStreamMethodRefImpl[R](viewClient, serializer, viewMethodProperties)
  }

  override def stream[T, A1, R](
      lambda: function.Function2[T, A1, View.QueryStreamEffect[R]]): ViewStreamMethodRef1[A1, R] = {
    val method = MethodRefResolver.resolveMethodRef(lambda)
    val viewMethodProperties = validateAndExtractViewMethodProperties[R](method)
    new ViewStreamMethodRefImpl1[A1, R](viewClient, serializer, viewMethodProperties)
  }
}
