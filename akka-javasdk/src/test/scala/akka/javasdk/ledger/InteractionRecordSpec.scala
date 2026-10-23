/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger

import java.time.Instant
import java.util.Optional

import scala.jdk.CollectionConverters._

import akka.javasdk.agent.Agent
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class InteractionRecordSpec extends AnyWordSpec with Matchers {
  import ToolPayloads._

  private val lookup = new ToolCall("c1", "getOrder", """{"orderId":"o_42"}""", """{"id":"o_42","status":"shipped"}""")
  private val refund = new ToolCall("c2", "issueRefund", """{"orderId":"o_42","amountCents":10}""", "")

  private val interaction = new InteractionRecord(
    "interaction-1",
    "session-1",
    "order-agent",
    Optional.empty(),
    new InteractionMetadata(
      new ModelConfig("openai", "gpt", "", 0.0, 1.0, 0, 0),
      Map.empty[String, String].asJava,
      Instant.EPOCH,
      Instant.EPOCH,
      InteractionMetadata.FinishReason.STOP),
    "system",
    List.empty[akka.javasdk.agent.MessageContent].asJava,
    List(
      new ModelResponse("m1", "", new Agent.TokenUsage(0, 0), "", List(lookup).asJava),
      new ModelResponse("m2", "", new Agent.TokenUsage(0, 0), "", List(refund).asJava),
      new ModelResponse("m3", "Refunded.", new Agent.TokenUsage(0, 0), "", List.empty[ToolCall].asJava)).asJava,
    List.empty[ToolCallResponse].asJava,
    Optional.empty(),
    Optional.empty(),
    Instant.EPOCH)

  "InteractionRecord" should {
    "list the calls of one tool in order" in {
      interaction.toolCalls("getOrder").asScala shouldBe Seq(lookup)
      interaction.toolCalls("capturePhoto").asScala shouldBe empty
    }

    "list the names of every tool call in order" in {
      interaction.toolNames().asScala shouldBe Seq("getOrder", "issueRefund")
    }
  }

  "ToolCall" should {
    "decode the arguments as a type" in {
      lookup.argumentsAs(classOf[GetOrder]) shouldBe new GetOrder("o_42")
    }

    "decode the response as a type, empty without a response" in {
      lookup.responseAs(classOf[Order]) shouldBe Optional.of(new Order("o_42", "shipped"))
      refund.responseAs(classOf[Order]) shouldBe Optional.empty()
    }

    "reject arguments that do not decode as the type" in {
      intercept[IllegalArgumentException] {
        new ToolCall("c3", "getOrder", "not json", "").argumentsAs(classOf[GetOrder])
      }.getMessage should include("getOrder")
    }
  }
}
