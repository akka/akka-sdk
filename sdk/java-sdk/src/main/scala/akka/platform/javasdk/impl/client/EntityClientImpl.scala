/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.client

import akka.annotation.InternalApi
import akka.http.scaladsl.model.ContentTypes
import akka.japi.function
import akka.util.ByteString
import akka.platform.javasdk.JsonSupport
import akka.platform.javasdk.Metadata
import akka.platform.javasdk.action.Action
import akka.platform.javasdk.client.ActionClient
import akka.platform.javasdk.client.ComponentInvokeOnlyMethodRef
import akka.platform.javasdk.client.ComponentInvokeOnlyMethodRef1
import akka.platform.javasdk.client.ComponentMethodRef
import akka.platform.javasdk.client.ComponentMethodRef1
import akka.platform.javasdk.client.EventSourcedEntityClient
import akka.platform.javasdk.client.NoEntryFoundException
import akka.platform.javasdk.client.ValueEntityClient
import akka.platform.javasdk.client.ViewClient
import akka.platform.javasdk.client.WorkflowClient
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity
import akka.platform.javasdk.impl.MetadataImpl
import akka.platform.javasdk.impl.MetadataImpl.toProtocol
import akka.platform.javasdk.impl.reflection.Reflect
import kalix.javasdk.spi.ActionRequest
import kalix.javasdk.spi.ActionType
import kalix.javasdk.spi.ComponentType
import kalix.javasdk.spi.EntityRequest
import kalix.javasdk.spi.EventSourcedEntityType
import kalix.javasdk.spi.ValueEntityType
import kalix.javasdk.spi.ViewRequest
import kalix.javasdk.spi.ViewType
import kalix.javasdk.spi.WorkflowType
import kalix.javasdk.spi.{ ActionClient => RuntimeActionClient }
import kalix.javasdk.spi.{ EntityClient => RuntimeEntityClient }
import kalix.javasdk.spi.{ ViewClient => RuntimeViewClient }
import akka.platform.javasdk.valueentity.ValueEntity
import akka.platform.javasdk.workflow.AbstractWorkflow

import scala.concurrent.ExecutionContext
import scala.jdk.FutureConverters.FutureOps
import scala.util.Failure
import scala.util.Success
import scala.util.Try

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

  // commands for methods that take a state as a first parameter and then the command
  protected def createMethodRef2[A1, R](lambda: akka.japi.function.Function2[_, _, _]): ComponentMethodRef1[A1, R] =
    createMethodRefForEitherArity(lambda)

  protected def createMethodRef[R](lambda: akka.japi.function.Function[_, _]): ComponentMethodRef[R] =
    createMethodRefForEitherArity[Nothing, R](lambda)

  private def createMethodRefForEitherArity[A1, R](lambda: AnyRef): ComponentMethodRefImpl[A1, R] = {
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
    new ComponentMethodRefImpl[AnyRef, R](
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

        DeferredCallImpl(
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
      }).asInstanceOf[ComponentMethodRefImpl[A1, R]]

  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class ValueEntityClientImpl(
    entityClient: RuntimeEntityClient,
    callMetadata: Option[Metadata],
    entityId: String)(implicit val executionContext: ExecutionContext)
    extends EntityClientImpl(classOf[ValueEntity[_]], ValueEntityType, entityClient, callMetadata, entityId)
    with ValueEntityClient {

  override def method[T, R](methodRef: function.Function[T, ValueEntity.Effect[R]]): ComponentMethodRef[R] =
    createMethodRef[R](methodRef)

  override def method[T, A1, R](
      methodRef: function.Function2[T, A1, ValueEntity.Effect[R]]): ComponentMethodRef1[A1, R] =
    createMethodRef2(methodRef)
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class EventSourcedEntityClientImpl(
    entityClient: RuntimeEntityClient,
    callMetadata: Option[Metadata],
    entityId: String)(implicit val executionContext: ExecutionContext)
    extends EntityClientImpl(
      classOf[EventSourcedEntity[_, _]],
      EventSourcedEntityType,
      entityClient,
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
    callMetadata: Option[Metadata],
    entityId: String)(implicit val executionContext: ExecutionContext)
    extends EntityClientImpl(classOf[AbstractWorkflow[_]], WorkflowType, entityClient, callMetadata, entityId)
    with WorkflowClient {

  override def method[T, R](methodRef: function.Function[T, AbstractWorkflow.Effect[R]]): ComponentMethodRef[R] =
    createMethodRef(methodRef)

  override def method[T, A1, R](
      methodRef: function.Function2[T, A1, AbstractWorkflow.Effect[R]]): ComponentMethodRef1[A1, R] =
    createMethodRef2(methodRef)
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class ViewClientImpl(viewClient: RuntimeViewClient, callMetadata: Option[Metadata])(implicit
    val executionContext: ExecutionContext)
    extends ViewClient {

  override def method[T, R](methodRef: function.Function[T, R]): ComponentInvokeOnlyMethodRef[R] =
    createMethodRefForEitherArity(methodRef)

  override def method[T, A1, R](methodRef: function.Function2[T, A1, R]): ComponentInvokeOnlyMethodRef1[A1, R] =
    createMethodRefForEitherArity(methodRef)

  private def createMethodRefForEitherArity[A1, R](lambda: AnyRef): ComponentMethodRefImpl[A1, R] = {
    val method = MethodRefResolver.resolveMethodRef(lambda)
    ViewCallValidator.validate(method)
    // extract view id
    val declaringClass = method.getDeclaringClass
    // FIXME Surprising that this isn' the view id declaringClass.getAnnotation(classOf[ViewId]).value()
    val serviceName = declaringClass.getName
    val methodName = method.getName.capitalize

    new ComponentMethodRefImpl[AnyRef, R](
      None,
      callMetadata,
      { (maybeMetadata, maybeArg) =>
        // Note: same path for 0 and 1 arg calls
        val serializedPayload = maybeArg match {
          case Some(arg) =>
            // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
            JsonSupport.encodeToAkkaByteString(arg)
          case None => ByteString.emptyByteString
        }

        DeferredCallImpl(
          maybeArg.orNull,
          maybeMetadata.getOrElse(Metadata.EMPTY).asInstanceOf[MetadataImpl],
          ViewType,
          serviceName,
          methodName,
          None,
          { metadata =>
            viewClient
              .query(
                ViewRequest(
                  serviceName,
                  methodName,
                  ContentTypes.`application/json`,
                  serializedPayload,
                  toProtocol(metadata.asInstanceOf[MetadataImpl]).getOrElse(
                    kalix.protocol.component.Metadata.defaultInstance)))
              .transform {
                case Success(reply) =>
                  // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
                  val returnType = Reflect.getReturnType(declaringClass, method)
                  if (reply.payload.isEmpty)
                    Failure(
                      new NoEntryFoundException(
                        s"No matching entry found when calling $declaringClass.${method.getName}"))
                  else Try(JsonSupport.parseBytes[R](reply.payload.toArrayUnsafe(), returnType.asInstanceOf[Class[R]]))
                case Failure(ex) => Failure(ex)
              }
              .asJava

          })
      },
      canBeDeferred = false).asInstanceOf[ComponentMethodRefImpl[A1, R]]

  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class ActionClientImpl(actionClient: RuntimeActionClient, callMetadata: Option[Metadata])(
    implicit val executionContext: ExecutionContext)
    extends ActionClient {
  override def method[T, R](methodRef: function.Function[T, Action.Effect[R]]): ComponentMethodRef[R] =
    createMethodRefForEitherArity(methodRef)

  override def method[T, A1, R](methodRef: function.Function2[T, A1, Action.Effect[R]]): ComponentMethodRef1[A1, R] =
    createMethodRefForEitherArity(methodRef)

  private def createMethodRefForEitherArity[A1, R](lambda: AnyRef): ComponentMethodRefImpl[A1, R] = {
    val method = MethodRefResolver.resolveMethodRef(lambda)
    val declaringClass = method.getDeclaringClass
    if (!Reflect.isAction(declaringClass))
      throw new IllegalArgumentException(
        "Use dedicated builder for calling " + declaringClass.getSuperclass.getSimpleName
        + " component method " + declaringClass.getSimpleName + "::" + method.getName + ". This builder is meant for Action component calls.")

    // FIXME Surprising that this isn' the view id declaringClass.getAnnotation(classOf[ViewId]).value()
    val serviceName = declaringClass.getName
    val methodName = method.getName.capitalize

    new ComponentMethodRefImpl[AnyRef, R](
      None,
      callMetadata,
      { (maybeMetadata: Option[Metadata], maybeArg: Option[AnyRef]) =>
        // Note: same path for 0 and 1 arg calls
        val serializedPayload = maybeArg match {
          case Some(arg) =>
            // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
            JsonSupport.encodeToAkkaByteString(arg)
          case None => ByteString.emptyByteString
        }

        DeferredCallImpl(
          maybeArg.orNull,
          maybeMetadata.getOrElse(Metadata.EMPTY).asInstanceOf[MetadataImpl],
          ActionType,
          serviceName,
          methodName,
          None,
          { metadata =>
            actionClient
              .call(
                ActionRequest(
                  serviceName,
                  methodName,
                  ContentTypes.`application/json`,
                  serializedPayload,
                  toProtocol(metadata.asInstanceOf[MetadataImpl]).getOrElse(
                    kalix.protocol.component.Metadata.defaultInstance)))
              .transform {
                case Success(reply) =>
                  // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
                  val returnType = Reflect.getReturnType(declaringClass, method)
                  if (reply.payload.isEmpty) Success(null.asInstanceOf[R])
                  else Try(JsonSupport.parseBytes[R](reply.payload.toArrayUnsafe(), returnType.asInstanceOf[Class[R]]))
                case Failure(ex) => Failure(ex)
              }
              .asJava
          })
      }).asInstanceOf[ComponentMethodRefImpl[A1, R]]
  }
}
