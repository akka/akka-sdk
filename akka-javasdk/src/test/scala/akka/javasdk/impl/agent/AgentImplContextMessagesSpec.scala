/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.time.Instant
import java.util.Optional

import akka.javasdk.agent.SessionHistory
import akka.javasdk.agent.SessionMessage
import akka.javasdk.agent.SessionMessage.MessageContent.TextMessageContent
import akka.javasdk.agent.SessionMessage.TokenUsage
import akka.runtime.sdk.spi.SpiAgent.ContextMessage
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AgentImplContextMessagesSpec extends AnyWordSpec with Matchers {

  private def history(sanitized: Boolean): SessionHistory = {
    val ts = Instant.EPOCH
    val contents = java.util.List.of[SessionMessage.MessageContent](new TextMessageContent("text"))
    new SessionHistory(
      java.util.List.of[SessionMessage](
        new SessionMessage.UserMessage(ts, "question", "agent", sanitized),
        new SessionMessage.MultimodalUserMessage(ts, contents, "agent", sanitized),
        new SessionMessage.AiMessage(
          ts,
          "answer",
          "agent",
          java.util.List.of(),
          Optional.empty(),
          TokenUsage.EMPTY,
          java.util.Map.of(),
          sanitized),
        new SessionMessage.ToolCallResponse(ts, "agent", "id1", "search", "result", sanitized),
        new SessionMessage.MultimodalToolCallResponse(ts, "agent", "id2", "render", contents, sanitized)),
      0L)
  }

  private def sanitizedOf(message: ContextMessage): Boolean =
    message match {
      case m: ContextMessage.UserMessage             => m.sanitized
      case m: ContextMessage.AiMessage               => m.sanitized
      case m: ContextMessage.ToolCallResponseMessage => m.sanitized
    }

  "AgentImpl.toSpiContextMessages" should {

    "pass the sanitized flag of each message to the runtime" in {
      AgentImpl.toSpiContextMessages(history(sanitized = true)).map(sanitizedOf) shouldBe Vector.fill(5)(true)
      AgentImpl.toSpiContextMessages(history(sanitized = false)).map(sanitizedOf) shouldBe Vector.fill(5)(false)
    }
  }
}
