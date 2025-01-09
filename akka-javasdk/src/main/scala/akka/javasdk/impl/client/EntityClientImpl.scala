/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import akka.annotation.InternalApi
import akka.japi.function
import akka.javasdk.Metadata
import akka.javasdk.client.ComponentDeferredMethodRef
import akka.javasdk.client.ComponentDeferredMethodRef1
import akka.javasdk.client.ComponentMethodRef
import akka.javasdk.client.ComponentMethodRef1
import akka.javasdk.client.EventSourcedEntityClient
import akka.javasdk.client.KeyValueEntityClient
import akka.javasdk.client.TimedActionClient
import akka.javasdk.client.WorkflowClient
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.ComponentDescriptorFactory
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.workflow.Workflow
import akka.runtime.sdk.spi.TimedActionRequest
import akka.runtime.sdk.spi.TimedActionType
import akka.runtime.sdk.spi.ComponentType
import akka.runtime.sdk.spi.EntityRequest
import akka.runtime.sdk.spi.EventSourcedEntityType
import akka.runtime.sdk.spi.KeyValueEntityType
import akka.runtime.sdk.spi.WorkflowType
import akka.runtime.sdk.spi.{ TimedActionClient => RuntimeTimedActionClient }
import akka.runtime.sdk.spi.{ EntityClient => RuntimeEntityClient }
import scala.concurrent.ExecutionContext
import scala.jdk.FutureConverters.FutureOps
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.BytesPayload

/**
 * INTERNAL API
 */
@InternalApi
private[impl] sealed abstract class EntityClientImpl(
    expectedComponentSuperclass: Class[_],
    componentType: ComponentType,
    entityClient: RuntimeEntityClient,
    serializer: JsonSerializer,
    callMetadata: Option[Metadata],
    entityId: String)(implicit executionContext: ExecutionContext) {

  // commands for methods that take a state as a first parameter and then the command
  protected def createMethodRef2[A1, R](lambda: akka.japi.function.Function2[_, _, _]): ComponentMethodRef1[A1, R] =
    createMethodRefForEitherArity(lambda)

  protected def createMethodRef[R](lambda: akka.japi.function.Function[_, _]): ComponentMethodRef[R] =
    createMethodRefForEitherArity[Nothing, R](lambda)

  private def createMethodRefForEitherArity[A1, R](lambda: AnyRef): ComponentMethodRefImpl[A1, R] = {
    import MetadataImpl.toSpi
    val method = MethodRefResolver.resolveMethodRef(lambda)
    val declaringClass = method.getDeclaringClass
    if (!expectedComponentSuperclass.isAssignableFrom(declaringClass)) {
      throw new IllegalArgumentException(s"$declaringClass is not a subclass of $expectedComponentSuperclass")
    }
    val componentId = ComponentDescriptorFactory.readComponentIdIdValue(declaringClass)
    val methodName = method.getName.capitalize

    // FIXME push some of this logic into the NativeomponentMethodRef
    //       will be easier to follow to do that instead of creating a lambda here and injecting into that
    new ComponentMethodRefImpl[AnyRef, R](
      Some(entityId),
      callMetadata,
      { (maybeMetadata, maybeArg) =>
        // Note: same path for 0 and 1 arg calls
        val serializedPayload = maybeArg match {
          case Some(arg) =>
            // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
            serializer.toBytes(arg)
          case None =>
            BytesPayload.empty
        }

        DeferredCallImpl(
          maybeArg.orNull,
          maybeMetadata.getOrElse(Metadata.EMPTY).asInstanceOf[MetadataImpl],
          componentType,
          componentId,
          methodName,
          Some(entityId),
          { metadata =>
            entityClient
              .send(new EntityRequest(componentId, entityId, methodName, serializedPayload, toSpi(metadata)))
              .map { reply =>
                // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
                val returnType = Reflect.getReturnType[R](declaringClass, method)
                serializer.fromBytes(returnType, reply.payload)
              }
              .asJava
          },
          serializer)
      }).asInstanceOf[ComponentMethodRefImpl[A1, R]]

  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class KeyValueEntityClientImpl(
    entityClient: RuntimeEntityClient,
    serializer: JsonSerializer,
    callMetadata: Option[Metadata],
    entityId: String)(implicit val executionContext: ExecutionContext)
    extends EntityClientImpl(
      classOf[KeyValueEntity[_]],
      KeyValueEntityType,
      entityClient,
      serializer,
      callMetadata,
      entityId)
    with KeyValueEntityClient {

  override def method[T, R](methodRef: function.Function[T, KeyValueEntity.Effect[R]]): ComponentMethodRef[R] =
    createMethodRef[R](methodRef)

  override def method[T, A1, R](
      methodRef: function.Function2[T, A1, KeyValueEntity.Effect[R]]): ComponentMethodRef1[A1, R] =
    createMethodRef2(methodRef)
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class EventSourcedEntityClientImpl(
    entityClient: RuntimeEntityClient,
    serializer: JsonSerializer,
    callMetadata: Option[Metadata],
    entityId: String)(implicit val executionContext: ExecutionContext)
    extends EntityClientImpl(
      classOf[EventSourcedEntity[_, _]],
      EventSourcedEntityType,
      entityClient,
      serializer,
      callMetadata,
      entityId)
    with EventSourcedEntityClient {

  override def method[T, R](methodRef: function.Function[T, EventSourcedEntity.Effect[R]]): ComponentMethodRef[R] =
    createMethodRef(methodRef)

  override def method[T, A1, R](
      methodRef: function.Function2[T, A1, EventSourcedEntity.Effect[R]]): ComponentMethodRef1[A1, R] =
    createMethodRef2(methodRef)
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class WorkflowClientImpl(
    entityClient: RuntimeEntityClient,
    serializer: JsonSerializer,
    callMetadata: Option[Metadata],
    entityId: String)(implicit val executionContext: ExecutionContext)
    extends EntityClientImpl(classOf[Workflow[_]], WorkflowType, entityClient, serializer, callMetadata, entityId)
    with WorkflowClient {

  override def method[T, R](methodRef: function.Function[T, Workflow.Effect[R]]): ComponentMethodRef[R] =
    createMethodRef(methodRef)

  override def method[T, A1, R](methodRef: function.Function2[T, A1, Workflow.Effect[R]]): ComponentMethodRef1[A1, R] =
    createMethodRef2(methodRef)
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class TimedActionClientImpl(
    timedActionClient: RuntimeTimedActionClient,
    serializer: JsonSerializer,
    callMetadata: Option[Metadata])(implicit val executionContext: ExecutionContext)
    extends TimedActionClient {
  override def method[T, R](methodRef: function.Function[T, TimedAction.Effect]): ComponentDeferredMethodRef[R] =
    createMethodRefForEitherArity(methodRef)

  override def method[T, A1, R](
      methodRef: function.Function2[T, A1, TimedAction.Effect]): ComponentDeferredMethodRef1[A1, R] =
    createMethodRefForEitherArity(methodRef)

  private def createMethodRefForEitherArity[A1, R](lambda: AnyRef): ComponentMethodRefImpl[A1, R] = {
    import MetadataImpl.toSpi
    val method = MethodRefResolver.resolveMethodRef(lambda)
    val declaringClass = method.getDeclaringClass
    if (!Reflect.isAction(declaringClass))
      throw new IllegalArgumentException(
        "Use dedicated builder for calling " + declaringClass.getSuperclass.getSimpleName
        + " component method " + declaringClass.getSimpleName + "::" + method.getName + ". This builder is meant for Action component calls.")
    val componentId = ComponentDescriptorFactory.readComponentIdIdValue(declaringClass)
    val methodName = method.getName.capitalize

    new ComponentMethodRefImpl[AnyRef, R](
      None,
      callMetadata,
      { (maybeMetadata: Option[Metadata], maybeArg: Option[AnyRef]) =>
        // Note: same path for 0 and 1 arg calls
        val serializedPayload = maybeArg match {
          case Some(arg) =>
            // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
            serializer.toBytes(arg)
          case None =>
            BytesPayload.empty
        }

        DeferredCallImpl(
          maybeArg.orNull,
          maybeMetadata.getOrElse(Metadata.EMPTY).asInstanceOf[MetadataImpl],
          TimedActionType,
          componentId,
          methodName,
          None,
          { metadata =>
            timedActionClient
              .call(new TimedActionRequest(componentId, methodName, serializedPayload, toSpi(metadata)))
              .transform {
                case Success(reply) =>
                  // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
                  val returnType = Reflect.getReturnType[R](declaringClass, method)
                  if (reply.payload.isEmpty) Success(null.asInstanceOf[R])
                  else Try(serializer.fromBytes(returnType, reply.payload))
                case Failure(ex) => Failure(ex)
              }
              .asJava
          },
          serializer)
      }).asInstanceOf[ComponentMethodRefImpl[A1, R]]
  }
}
