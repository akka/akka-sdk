/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.client

import akka.annotation.InternalApi
import akka.http.scaladsl.model.ContentTypes
import akka.japi.function
import akka.util.ByteString
import kalix.javasdk.JsonSupport
import kalix.javasdk.Metadata
import kalix.javasdk.client.EventSourcedEntityClient
import kalix.javasdk.client.NativeComponentMethodRef
import kalix.javasdk.client.NativeComponentMethodRef1
import kalix.javasdk.client.ValueEntityClient
import kalix.javasdk.eventsourcedentity.EventSourcedEntity
import kalix.javasdk.impl.MetadataImpl
import kalix.javasdk.impl.MetadataImpl.toProtocol
import kalix.javasdk.impl.reflection.Reflect
import kalix.javasdk.spi.EntityRequest
import kalix.javasdk.spi.{ EntityClient => RuntimeEntityClient }
import kalix.javasdk.valueentity.ValueEntity
import kalix.javasdk.client.WorkflowClient
import kalix.javasdk.spi.ComponentType
import kalix.javasdk.spi.EventSourcedEntityType
import kalix.javasdk.spi.ValueEntityType
import kalix.javasdk.spi.WorkflowType
import kalix.javasdk.workflow.AbstractWorkflow

import java.util.Optional
import scala.concurrent.ExecutionContext
import scala.jdk.FutureConverters.FutureOps
import scala.jdk.OptionConverters.RichOptional

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] sealed abstract class EntityClientImpl(
    expectedComponentSuperclass: Class[_],
    componentType: ComponentType,
    entityClient: RuntimeEntityClient,
    callMetadata: Option[Metadata],
    entityId: String)(implicit executionContext: ExecutionContext) {

  def this(
      expectedComponentSuperclass: Class[_],
      componentType: ComponentType,
      entityClient: RuntimeEntityClient,
      callMetadata: Optional[Metadata],
      entityId: String)(implicit executionContext: ExecutionContext) =
    this(expectedComponentSuperclass, componentType, entityClient, callMetadata.toScala, entityId)(executionContext)

  // commands for methods that take a state as a first parameter and then the command
  protected def createMethodRef2[A1, R](
      lambda: akka.japi.function.Function2[_, _, _]): NativeComponentMethodRef1[A1, R] =
    createMethodRefForEitherArity(lambda)

  protected def createMethodRef[R](lambda: akka.japi.function.Function[_, _]): NativeComponentMethodRef[R] =
    createMethodRefForEitherArity[Nothing, R](lambda)

  private def createMethodRefForEitherArity[A1, R](lambda: AnyRef): NativeComponentMethodRefImpl[A1, R] = {
    val method = MethodRefResolver.resolveMethodRef(lambda)
    val declaringClass = method.getDeclaringClass
    if (!expectedComponentSuperclass.isAssignableFrom(declaringClass)) {
      throw new IllegalArgumentException(s"$declaringClass is not a subclass of $expectedComponentSuperclass")
    }
    // FIXME Surprising that this isn' from the annotation: Reflect.entityTypeOf(declaringClass)
    val entityType = declaringClass.getName
    val serviceName = declaringClass.getName // ?? full service name is the class name?
    val methodName = method.getName.capitalize

    // FIXME push some of this logic into the NativeomponentMethodRef
    //       will be easier to follow to do that instead of creating a lambda here and injecting into that
    new NativeComponentMethodRefImpl[AnyRef, R](
      Some(entityId),
      callMetadata,
      { (maybeMetadata, maybeArg) =>
        // Note: same path for 0 and 1 arg calls
        val serializedPayload = maybeArg match {
          case Some(arg) =>
            // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
            JsonSupport.encodeToAkkaByteString(arg)
          case None => ByteString.emptyByteString
        }

        EmbeddedDeferredCall(
          maybeArg.orNull,
          maybeMetadata.getOrElse(Metadata.EMPTY).asInstanceOf[MetadataImpl],
          componentType,
          serviceName,
          methodName,
          Some(entityId),
          { metadata =>
            entityClient
              .send(
                EntityRequest(
                  entityType,
                  entityId,
                  methodName,
                  ContentTypes.`application/json`,
                  serializedPayload,
                  toProtocol(metadata.asInstanceOf[MetadataImpl]).getOrElse(
                    kalix.protocol.component.Metadata.defaultInstance)))
              .map { reply =>
                // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
                val returnType = Reflect.getReturnType(declaringClass, method)
                JsonSupport.parseBytes[R](reply.payload.toArrayUnsafe(), returnType.asInstanceOf[Class[R]])
              }
              .asJava
          })
      }).asInstanceOf[NativeComponentMethodRefImpl[A1, R]]

  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class ValueEntityClientImpl(
    entityClient: RuntimeEntityClient,
    callMetadata: Optional[Metadata],
    entityId: String)(implicit val executionContext: ExecutionContext)
    extends EntityClientImpl(classOf[ValueEntity[_]], ValueEntityType, entityClient, callMetadata, entityId)
    with ValueEntityClient {

  override def method[T, R](methodRef: function.Function[T, ValueEntity.Effect[R]]): NativeComponentMethodRef[R] =
    createMethodRef[R](methodRef)

  override def method[T, A1, R](
      methodRef: function.Function2[T, A1, ValueEntity.Effect[R]]): NativeComponentMethodRef1[A1, R] =
    createMethodRef2(methodRef)
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class EventSourcedEntityClientImpl(
    entityClient: RuntimeEntityClient,
    callMetadata: Optional[Metadata],
    entityId: String)(implicit val executionContext: ExecutionContext)
    extends EntityClientImpl(
      classOf[EventSourcedEntity[_, _]],
      EventSourcedEntityType,
      entityClient,
      callMetadata,
      entityId)
    with EventSourcedEntityClient {

  override def method[T, R](
      methodRef: function.Function[T, EventSourcedEntity.Effect[R]]): NativeComponentMethodRef[R] =
    createMethodRef(methodRef)

  override def method[T, A1, R](
      methodRef: function.Function2[T, A1, EventSourcedEntity.Effect[R]]): NativeComponentMethodRef1[A1, R] =
    createMethodRef2(methodRef)
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class WorkflowClientImpl(
    entityClient: RuntimeEntityClient,
    callMetadata: Optional[Metadata],
    entityId: String)(implicit val executionContext: ExecutionContext)
    extends EntityClientImpl(classOf[AbstractWorkflow[_]], WorkflowType, entityClient, callMetadata, entityId)
    with WorkflowClient {

  override def method[T, R](methodRef: function.Function[T, AbstractWorkflow.Effect[R]]): NativeComponentMethodRef[R] =
    createMethodRef(methodRef)

  override def method[T, A1, R](
      methodRef: function.Function2[T, A1, AbstractWorkflow.Effect[R]]): NativeComponentMethodRef1[A1, R] =
    createMethodRef2(methodRef)
}
