/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.spi

import java.util.Optional

import scala.concurrent.Future

import akka.annotation.InternalApi
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.ComponentDescriptor
import akka.runtime.sdk.spi.ComponentType
import akka.runtime.sdk.spi.KeyValueEntityType
import akka.runtime.sdk.spi.SpiMetadata

// FIXME move to runtime

@InternalApi
object SpiAgent {
  final class Command(val name: String, val payload: Option[BytesPayload], val metadata: SpiMetadata)

  final class Error(val description: String)
}

@InternalApi
object SpiChatAgent {
  type Reply = BytesPayload

  final class FactoryContext(val sessionId: Optional[String])

  sealed trait Effect

  final class RequestModelEffect(val systemMessage: String, val userMessage: String, val replyMetadata: SpiMetadata)
      extends Effect

  final class ReplyEffect(val reply: Reply, val metadata: SpiMetadata) extends Effect

  final class ErrorEffect(val error: SpiAgent.Error) extends Effect

}

@InternalApi
trait SpiChatAgent {

  def handleCommand(command: SpiAgent.Command): Future[SpiChatAgent.Effect]

}

// FIXME move to runtime ChatAgentDescriptor

/**
 * INTERNAL API
 */
@InternalApi
final class ChatAgentDescriptor(
    val componentId: String,
    val implementationName: String,
    val instanceFactory: SpiChatAgent.FactoryContext => SpiChatAgent)
    extends ComponentDescriptor {

  override val componentType: ComponentType = KeyValueEntityType // FIXME ChatAgentType

  def withInstanceFactory(f: SpiChatAgent.FactoryContext => SpiChatAgent): ChatAgentDescriptor =
    new ChatAgentDescriptor(componentId, implementationName, f)

}

// FIXME move to runtime ComponentClients

/**
 * INTERNAL API
 */
@InternalApi
final class AgentRequest(
    val agentId: String,
    val sessionId: Optional[String],
    val methodName: String,
    val payload: BytesPayload,
    val metadata: SpiMetadata)

/**
 * INTERNAL API
 */
@InternalApi
final class AgentResult(val payload: BytesPayload, val metadata: SpiMetadata)

/**
 * INTERNAL API
 */
@InternalApi
trait AgentClient {
  def send(request: AgentRequest): Future[AgentResult]
}
