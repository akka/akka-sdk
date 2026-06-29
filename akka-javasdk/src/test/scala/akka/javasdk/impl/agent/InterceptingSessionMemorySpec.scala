/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.time.Instant
import java.util
import java.util.Optional

import scala.collection.mutable
import scala.jdk.CollectionConverters._

import akka.javasdk.agent.SessionHistory
import akka.javasdk.agent.SessionMemory
import akka.javasdk.agent.SessionMemoryInterceptor
import akka.javasdk.agent.SessionMessage
import akka.javasdk.agent.SessionMessage.AiMessage
import akka.javasdk.agent.SessionMessage.MessageContent.TextMessageContent
import akka.javasdk.agent.SessionMessage.MultimodalToolCallResponse
import akka.javasdk.agent.SessionMessage.MultimodalUserMessage
import akka.javasdk.agent.SessionMessage.ToolCallResponse
import akka.javasdk.agent.SessionMessage.UserMessage
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class InterceptingSessionMemorySpec extends AnyWordSpec with Matchers {

  /** Records every call made to it for inspection in assertions. */
  class RecordingSessionMemory extends SessionMemory {
    val calls: mutable.Buffer[String] = mutable.Buffer.empty
    var lastMessages: util.List[SessionMessage] = util.List.of()

    override def addInteraction(
        sessionId: String,
        userMessage: UserMessage,
        messages: util.List[SessionMessage]): Unit = {
      calls += s"text:$sessionId:[${userMessage.text()}]"
      lastMessages = messages
    }

    override def addInteraction(
        sessionId: String,
        userMessage: MultimodalUserMessage,
        messages: util.List[SessionMessage]): Unit = {
      val texts =
        userMessage
          .contents()
          .asScala
          .map(c => c.asInstanceOf[TextMessageContent].text())
          .mkString("|")
      calls += s"multimodal:$sessionId:[$texts]"
      lastMessages = messages
    }

    override def getHistory(sessionId: String): SessionHistory = {
      calls += s"getHistory:$sessionId"
      SessionHistory.EMPTY
    }
  }

  private def userMessage(text: String): UserMessage =
    new UserMessage(Instant.EPOCH, text, "test-component")

  private def multimodalMessage(): MultimodalUserMessage =
    new MultimodalUserMessage(Instant.EPOCH, util.List.of(new TextMessageContent("hi")), "test-component")

  "InterceptingSessionMemory" should {

    "pass through both overloads with identity interceptor" in {
      val delegate = new RecordingSessionMemory
      val interceptor = new SessionMemoryInterceptor {}
      val memory = new InterceptingSessionMemory(delegate, interceptor)

      memory.addInteraction("s1", userMessage("hello"), util.List.of())
      memory.addInteraction("s2", multimodalMessage(), util.List.of())

      delegate.calls.toList shouldBe List("text:s1:[hello]", "multimodal:s2:[hi]")
    }

    "reflect text transform in delegate" in {
      val delegate = new RecordingSessionMemory
      val interceptor = new SessionMemoryInterceptor {
        override def beforeWrite(sessionId: String, userMessage: UserMessage): UserMessage =
          new UserMessage(userMessage.timestamp(), "redacted", userMessage.componentId())
      }
      val memory = new InterceptingSessionMemory(delegate, interceptor)

      memory.addInteraction("s1", userMessage("secret"), util.List.of())
      memory.addInteraction("s1", multimodalMessage(), util.List.of())

      delegate.calls.toList shouldBe List("text:s1:[redacted]", "multimodal:s1:[hi]")
    }

    "reflect multimodal transform in delegate" in {
      val delegate = new RecordingSessionMemory
      val interceptor = new SessionMemoryInterceptor {
        override def beforeWrite(sessionId: String, userMessage: MultimodalUserMessage): MultimodalUserMessage =
          new MultimodalUserMessage(
            userMessage.timestamp(),
            util.List.of(new TextMessageContent("a"), new TextMessageContent("b")),
            userMessage.componentId())
      }
      val memory = new InterceptingSessionMemory(delegate, interceptor)

      memory.addInteraction("s1", userMessage("hi"), util.List.of())
      memory.addInteraction("s2", multimodalMessage(), util.List.of())

      delegate.calls.toList shouldBe List("text:s1:[hi]", "multimodal:s2:[a|b]")
    }

    "reflect ai message transform in messages list" in {
      val delegate = new RecordingSessionMemory
      val interceptor = new SessionMemoryInterceptor {
        override def beforeWrite(sessionId: String, aiMessage: AiMessage): AiMessage =
          // strip thinking
          new AiMessage(
            aiMessage.timestamp(),
            aiMessage.text(),
            aiMessage.componentId(),
            aiMessage.toolCallRequests(),
            Optional.empty(),
            aiMessage.tokenUsage(),
            aiMessage.attributes())
      }
      val memory = new InterceptingSessionMemory(delegate, interceptor)

      val ai = new AiMessage(Instant.EPOCH, "answer", "agent", util.List.of(), Optional.of("internal thoughts"))
      memory.addInteraction("s1", userMessage("hi"), util.List.of(ai))

      delegate.lastMessages.size() shouldBe 1
      val seen = delegate.lastMessages.get(0).asInstanceOf[AiMessage]
      seen.thinking() shouldBe Optional.empty()
      seen.text() shouldBe "answer"
    }

    "reflect tool call response transform in messages list" in {
      val delegate = new RecordingSessionMemory
      val interceptor = new SessionMemoryInterceptor {
        override def beforeWrite(sessionId: String, tcr: ToolCallResponse): ToolCallResponse =
          new ToolCallResponse(tcr.timestamp(), tcr.componentId(), tcr.id(), tcr.name(), "this is a short one")
      }
      val memory = new InterceptingSessionMemory(delegate, interceptor)

      val tcr = new ToolCallResponse(Instant.EPOCH, "agent", "id1", "search", "this is a long response")
      memory.addInteraction("s1", userMessage("hi"), util.List.of(tcr))

      delegate.lastMessages.size() shouldBe 1
      val seen = delegate.lastMessages.get(0).asInstanceOf[ToolCallResponse]
      seen.text() shouldBe "this is a short one"
    }

    "reflect multimodal tool call response transform in messages list" in {
      val delegate = new RecordingSessionMemory
      val interceptor = new SessionMemoryInterceptor {
        override def beforeWrite(sessionId: String, mtcr: MultimodalToolCallResponse): MultimodalToolCallResponse =
          new MultimodalToolCallResponse(
            mtcr.timestamp(),
            mtcr.componentId(),
            mtcr.id(),
            mtcr.name(),
            util.List.of(new TextMessageContent("redacted")))
      }
      val memory = new InterceptingSessionMemory(delegate, interceptor)

      val mtcr = new MultimodalToolCallResponse(
        Instant.EPOCH,
        "agent",
        "id1",
        "render_chart",
        util.List.of(new TextMessageContent("caption"), new TextMessageContent("more")))
      memory.addInteraction("s1", userMessage("hi"), util.List.of(mtcr))

      delegate.lastMessages.size() shouldBe 1
      val seen = delegate.lastMessages.get(0).asInstanceOf[MultimodalToolCallResponse]
      seen.contents().asScala.map(_.asInstanceOf[TextMessageContent].text()).toList shouldBe List("redacted")
    }
  }
}
