/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import akka.annotation.InternalApi
import akka.javasdk.a2a.A2aMessage
import akka.javasdk.a2a.A2aPart
import akka.javasdk.a2a.A2aReply
import akka.javasdk.a2a.AbstractA2aEndpoint
import akka.javasdk.annotations.Acl
import akka.javasdk.annotations.JWT
import akka.javasdk.annotations.a2a.A2aEndpoint
import akka.javasdk.impl.AclDescriptorFactory.deriveAclOptions
import akka.javasdk.impl.JwtDescriptorFactory.deriveJWTOptions
import akka.javasdk.impl.telemetry.Telemetry
import akka.runtime.sdk.spi.A2aEndpointConstructionContext
import akka.runtime.sdk.spi.A2aEndpointDescriptor
import akka.runtime.sdk.spi.A2aEndpointDescriptor.Card
import akka.runtime.sdk.spi.A2aEndpointDescriptor.DataPart
import akka.runtime.sdk.spi.A2aEndpointDescriptor.FilePart
import akka.runtime.sdk.spi.A2aEndpointDescriptor.Provider
import akka.runtime.sdk.spi.A2aEndpointDescriptor.Reply
import akka.runtime.sdk.spi.A2aEndpointDescriptor.Skill
import akka.runtime.sdk.spi.A2aEndpointDescriptor.TextPart
import akka.runtime.sdk.spi.ComponentOptions

/**
 * INTERNAL API
 */
@InternalApi
object A2aEndpointDescriptorFactory {

  /** What the SDK derives from the call for the request context of the endpoint. */
  final case class CallContext(callerId: Option[String], contextId: String, sessionId: String)

  def callContext(context: A2aEndpointConstructionContext, messageContextId: Option[String]): CallContext = {
    val callerId = context.jwt
      .flatMap { claims =>
        claims.getStringClaim("sub").map(subject => s"jwt:${claims.getStringClaim("iss").getOrElse("")}:$subject")
      }
      .orElse(context.principal.service.map(service => s"service:$service"))
    val contextId = messageContextId.getOrElse(UUID.randomUUID().toString)
    // The caller picks the context id, so only a caller with an identity can have a session that lasts.
    val sessionId = callerId match {
      case Some(id) => sessionIdFor(id, contextId)
      case None     => UUID.randomUUID().toString
    }
    CallContext(callerId, contextId, sessionId)
  }

  private[impl] def sessionIdFor(callerId: String, contextId: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(callerId.getBytes(StandardCharsets.UTF_8))
    digest.update(0.toByte)
    digest.update(contextId.getBytes(StandardCharsets.UTF_8))
    digest.digest().take(16).map(b => f"${b & 0xff}%02x").mkString
  }

  def apply[T <: AbstractA2aEndpoint](
      endpointClass: Class[T],
      instanceFactory: (A2aEndpointConstructionContext, CallContext) => T)(implicit
      sdkExecutor: ExecutionContext): A2aEndpointDescriptor = {

    val annotation = endpointClass.getAnnotation(classOf[A2aEndpoint])

    val skills = annotation.skills().toSeq.map { skill =>
      new Skill(skill.id(), skill.name(), skill.description(), skill.tags().toSeq, skill.examples().toSeq)
    }
    val duplicateSkillIds = skills.groupBy(_.id).collect { case (id, same) if same.size > 1 => id }
    if (duplicateSkillIds.nonEmpty)
      throw ValidationException(
        s"A2A skills with duplicated ids exist in ${endpointClass.getName}: [${duplicateSkillIds.mkString(", ")}], make sure that each skill has a unique id")

    val provider =
      if (annotation.providerOrganization().isBlank) None
      else Some(new Provider(annotation.providerOrganization(), annotation.providerUrl()))

    def handle(context: A2aEndpointConstructionContext, message: A2aEndpointDescriptor.Message): Future[Reply] =
      Future {
        val call = callContext(context, message.contextId)
        Telemetry.withCurrentContext(Option(context.telemetryContext)) {
          val reply = instanceFactory(context, call).onMessage(toSdkMessage(message))
          if (reply == null)
            throw new IllegalStateException(
              s"A2A endpoint [${endpointClass.getName}] returned null from onMessage, it must return a reply")
          toSpiReply(reply, call.contextId)
        }
      }

    new A2aEndpointDescriptor(
      endpointPath = annotation.path(),
      implementationName = endpointClass.getName,
      componentOptions = new ComponentOptions(
        deriveAclOptions(Option(endpointClass.getAnnotation(classOf[Acl]))),
        deriveJWTOptions(Option(endpointClass.getAnnotation(classOf[JWT])), endpointClass.getCanonicalName)),
      card = new Card(annotation.name(), annotation.description(), annotation.version(), provider, skills),
      handler = handle,
      provided = false)
  }

  private def toSdkMessage(message: A2aEndpointDescriptor.Message): A2aMessage =
    new A2aMessage(
      message.messageId,
      message.contextId.toJava,
      message.parts.map(toSdkPart).asJava,
      message.metadata.asJava.asInstanceOf[java.util.Map[String, Object]])

  private def toSdkPart(part: A2aEndpointDescriptor.Part): A2aPart = part match {
    case text: TextPart => new A2aPart.TextPart(text.text)
    case data: DataPart => new A2aPart.DataPart(data.data.asJava.asInstanceOf[java.util.Map[String, Object]])
    case file: FilePart =>
      new A2aPart.FilePart(file.uri.toJava, file.bytes.toJava, file.mediaType.toJava, file.filename.toJava)
  }

  private def toSpiReply(reply: A2aReply, contextId: String): Reply =
    new Reply(reply.parts().asScala.toSeq.map(toSpiPart), reply.metadata().asScala.toMap[String, Any], Some(contextId))

  private def toSpiPart(part: A2aPart): A2aEndpointDescriptor.Part = part match {
    case text: A2aPart.TextPart => new TextPart(text.text())
    case data: A2aPart.DataPart => new DataPart(data.data().asScala.toMap[String, Any])
    case file: A2aPart.FilePart =>
      new FilePart(file.uri().toScala, file.bytes().toScala, file.mediaType().toScala, file.filename().toScala)
  }
}
