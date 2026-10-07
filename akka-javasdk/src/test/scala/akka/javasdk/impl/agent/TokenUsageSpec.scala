/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.Optional

import akka.javasdk.JsonSupport
import akka.javasdk.agent.Agent
import akka.javasdk.agent.SessionMemoryEntity
import akka.javasdk.agent.SessionMessage
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.client.AgentInvokeReplyOnlyMethodRefImpl
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiMetadataEntry
import com.fasterxml.jackson.databind.node.ObjectNode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TokenUsageSpec extends AnyWordSpec with Matchers {

  private def decode[T](cls: Class[T], json: String): T = JsonSupport.decodeJson(cls, json.getBytes(UTF_8))

  "SessionMessage.TokenUsage" should {

    "default totalInputTokens to inputTokens when missing" in {
      val usage = decode(classOf[SessionMessage.TokenUsage], """{"inputTokens":100,"outputTokens":20}""")
      usage shouldBe new SessionMessage.TokenUsage(100, 20, 0, 0, 100)
    }

    "default totalInputTokens to inputTokens in a stored AiMessageAdded event without it" in {
      val event = new SessionMemoryEntity.Event.AiMessageAdded(
        Instant.EPOCH,
        "agent",
        "hello",
        5,
        5,
        java.util.List.of(),
        Optional.empty(),
        Optional.of(new SessionMessage.TokenUsage(100, 20)),
        java.util.Map.of())
      val json = JsonSupport.getObjectMapper.valueToTree[ObjectNode](event)
      val tokenUsage = json.get("tokenUsage").asInstanceOf[ObjectNode]
      tokenUsage.remove(java.util.List.of("cacheReadInputTokens", "cacheWriteInputTokens", "totalInputTokens"))

      val decoded = decode(classOf[SessionMemoryEntity.Event.AiMessageAdded], json.toString)
      decoded.tokenUsage().get() shouldBe new SessionMessage.TokenUsage(100, 20, 0, 0, 100)
    }

    "sum all counts" in {
      val first = new SessionMessage.TokenUsage(100, 20, 11, 22, 133)
      val second = new SessionMessage.TokenUsage(200, 30, 5, 0, 205)
      first.add(second) shouldBe new SessionMessage.TokenUsage(300, 50, 16, 22, 338)
    }

    "stop the sum at Int.MaxValue instead of overflowing" in {
      val large = new SessionMessage.TokenUsage(Int.MaxValue - 1, 1, 0, 0, Int.MaxValue - 1)
      large.add(new SessionMessage.TokenUsage(10, 1, 0, 0, 10)) shouldBe
      new SessionMessage.TokenUsage(Int.MaxValue, 2, 0, 0, Int.MaxValue)
    }

    "copy all counts from an Agent.TokenUsage" in {
      SessionMessage.TokenUsage.from(new Agent.TokenUsage(100, 20, 11, 22, 133)) shouldBe
      new SessionMessage.TokenUsage(100, 20, 11, 22, 133)
    }
  }

  "Agent.TokenUsage" should {

    "default totalInputTokens to inputTokens when missing" in {
      val usage = decode(classOf[Agent.TokenUsage], """{"inputTokens":100,"outputTokens":20}""")
      usage shouldBe new Agent.TokenUsage(100, 20, 0, 0, 100)
    }
  }

  "AgentImpl.toSessionTokenUsage" should {

    "map all counts of the runtime token usage" in {
      AgentImpl.toSessionTokenUsage(new SpiAgent.SpiTokenUsage(100, 20, 11, 22, 133)) shouldBe
      new SessionMessage.TokenUsage(100, 20, 11, 22, 133)
    }
  }

  "AgentInvokeReplyOnlyMethodRefImpl.toTokenUsage" should {

    "read all counts from the reply metadata" in {
      val metadata = MetadataImpl.of(
        Seq(
          new SpiMetadataEntry(SpiAgent.AgentInputTokensKey, "100"),
          new SpiMetadataEntry(SpiAgent.AgentOutputTokensKey, "20"),
          new SpiMetadataEntry(SpiAgent.AgentCacheReadTokensKey, "11"),
          new SpiMetadataEntry(SpiAgent.AgentCacheWriteTokensKey, "22"),
          new SpiMetadataEntry(SpiAgent.AgentEffectiveInputTokensKey, "133")))

      AgentInvokeReplyOnlyMethodRefImpl.toTokenUsage(metadata) shouldBe new Agent.TokenUsage(100, 20, 11, 22, 133)
    }

    "default totalInputTokens to inputTokens when the metadata has no cache counts" in {
      val metadata = MetadataImpl.of(
        Seq(
          new SpiMetadataEntry(SpiAgent.AgentInputTokensKey, "100"),
          new SpiMetadataEntry(SpiAgent.AgentOutputTokensKey, "20")))

      AgentInvokeReplyOnlyMethodRefImpl.toTokenUsage(metadata) shouldBe new Agent.TokenUsage(100, 20, 0, 0, 100)
    }
  }
}
