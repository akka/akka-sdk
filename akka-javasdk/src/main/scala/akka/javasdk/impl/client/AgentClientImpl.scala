/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import scala.concurrent.ExecutionContext
import scala.jdk.FutureConverters.FutureOps

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.japi.function
import akka.javasdk.Metadata
import akka.javasdk.agent.Agent
import akka.javasdk.client.AgentClient
import akka.javasdk.client.AgentClientInSession
import akka.javasdk.client.ComponentMethodRef
import akka.javasdk.client.ComponentMethodRef1
import akka.javasdk.impl.ComponentDescriptorFactory
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.AgentRequest
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.AgentType
import akka.runtime.sdk.spi.{ AgentClient => RuntimeAgentClient }

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class AgentClientImpl(
    agentClient: RuntimeAgentClient,
    serializer: JsonSerializer,
    callMetadata: Option[Metadata],
    sessionId: String)(implicit val executionContext: ExecutionContext, system: ActorSystem[_])
    extends AgentClient
    with AgentClientInSession {

  override def inSession(sessionId: String): AgentClientInSession = {
    if ((sessionId eq null) || sessionId.trim.isBlank)
      throw new IllegalArgumentException("sessionId must be defined")
    AgentClientImpl(agentClient, serializer, callMetadata, sessionId)
  }

  override def method[T, R](methodRef: function.Function[T, Agent.Effect[R]]): ComponentMethodRef[R] =
    createMethodRef[R](methodRef)

  override def method[T, A1, R](methodRef: function.Function2[T, A1, Agent.Effect[R]]): ComponentMethodRef1[A1, R] =
    createMethodRef2(methodRef)

  // commands for methods that take a state as a first parameter and then the command
  protected def createMethodRef2[A1, R](lambda: akka.japi.function.Function2[_, _, _]): ComponentMethodRef1[A1, R] =
    createMethodRefForEitherArity(lambda)

  protected def createMethodRef[R](lambda: akka.japi.function.Function[_, _]): ComponentMethodRef[R] =
    createMethodRefForEitherArity[Nothing, R](lambda)

  private def createMethodRefForEitherArity[A1, R](lambda: AnyRef): ComponentMethodRefImpl[A1, R] = {
    import MetadataImpl.toSpi
    val method = MethodRefResolver.resolveMethodRef(lambda)
    val declaringClass = method.getDeclaringClass
    val expectedComponentSuperclass: Class[_] = classOf[Agent]
    if (!expectedComponentSuperclass.isAssignableFrom(declaringClass)) {
      throw new IllegalArgumentException(s"$declaringClass is not a subclass of $expectedComponentSuperclass")
    }
    val componentId = ComponentDescriptorFactory.readComponentIdValue(declaringClass)
    val methodName = method.getName.capitalize
    val returnType = Reflect.getReturnType(declaringClass, method)

    // FIXME push some of this logic into the NativeomponentMethodRef
    //       will be easier to follow to do that instead of creating a lambda here and injecting into that
    new ComponentMethodRefImpl[AnyRef, R](
      optionalId = None,
      callMetadata,
      { (maybeMetadata, maybeRetrySetting, maybeArg) =>
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
          AgentType,
          componentId,
          methodName,
          entityId = None, {
            def callAgent(metadata: Metadata) = {
              agentClient
                .send(new AgentRequest(componentId, sessionId, methodName, serializedPayload, toSpi(metadata)))
                .map { reply =>
                  // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
                  serializer.fromBytes[R](returnType, reply.payload)
                }
            }
            metadata =>
              maybeRetrySetting match {
                case Some(retrySetting) =>
                  akka.pattern
                    .retry(retrySetting) { () =>
                      callAgent(metadata)
                    }
                    .asJava
                case None => callAgent(metadata).asJava
              }
          },
          serializer)
      },
      canBeDeferred = false)
      .asInstanceOf[ComponentMethodRefImpl[A1, R]]

  }

}
