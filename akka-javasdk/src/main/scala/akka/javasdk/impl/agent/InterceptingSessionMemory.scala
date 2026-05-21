/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.javasdk.agent.SessionHistory
import akka.javasdk.agent.SessionMemory
import akka.javasdk.agent.SessionMemoryInterceptor
import akka.javasdk.agent.SessionMessage

/** INTERNAL USE Not for user extension or instantiation */
@InternalApi
final class InterceptingSessionMemory(delegate: SessionMemory, interceptor: SessionMemoryInterceptor)
    extends SessionMemory {

  override def addInteraction(
      sessionId: String,
      userMessage: SessionMessage.UserMessage,
      messages: util.List[SessionMessage]): Unit =
    delegate.addInteraction(
      sessionId,
      requireNonNullResult(interceptor.beforeWrite(sessionId, userMessage), "UserMessage"),
      transformMessages(sessionId, messages))

  override def addInteraction(
      sessionId: String,
      userMessage: SessionMessage.MultimodalUserMessage,
      messages: util.List[SessionMessage]): Unit =
    delegate.addInteraction(
      sessionId,
      requireNonNullResult(interceptor.beforeWrite(sessionId, userMessage), "MultimodalUserMessage"),
      transformMessages(sessionId, messages))

  override def getHistory(sessionId: String): SessionHistory =
    delegate.getHistory(sessionId)

  private def transformMessages(sessionId: String, messages: util.List[SessionMessage]): util.List[SessionMessage] =
    messages.asScala.map(m => dispatch(sessionId, m)).toList.asJava

  private def dispatch(sessionId: String, message: SessionMessage): SessionMessage = message match {
    case ai: SessionMessage.AiMessage =>
      requireNonNullResult(interceptor.beforeWrite(sessionId, ai), "AiMessage")
    case tcr: SessionMessage.ToolCallResponse =>
      requireNonNullResult(interceptor.beforeWrite(sessionId, tcr), "ToolCallResponse")
    // UserMessage and MultimodalUserMessage are never present in the messages list — the user
    // message is passed as a separate argument to addInteraction. These cases exist only to
    // satisfy exhaustiveness on the sealed SessionMessage interface; pass through unchanged.
    case um: SessionMessage.UserMessage            => um
    case mum: SessionMessage.MultimodalUserMessage => mum
  }

  private def requireNonNullResult[T <: SessionMessage](result: T, messageType: String): T = {
    if (result == null)
      throw new NullPointerException(s"SessionMemoryInterceptor.beforeWrite($messageType) returned null")
    result
  }
}
