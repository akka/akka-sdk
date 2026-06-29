/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.net.URI
import java.time.Instant

import scala.jdk.CollectionConverters._

import akka.javasdk.agent.SessionMessage
import akka.runtime.sdk.spi.SpiAgent
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AgentImplToolResponseSpec extends AnyWordSpec with Matchers {

  private def toSessionMessage(contents: Seq[SpiAgent.MessageContent]): SessionMessage =
    AgentImpl.toSessionToolCallResponse(Instant.EPOCH, "agent", "id1", "render_chart", contents)

  "AgentImpl.toSessionToolCallResponse" should {

    "collapse a single text content to a legacy ToolCallResponse" in {
      toSessionMessage(Seq(new SpiAgent.TextMessageContent("just text"))) match {
        case tcr: SessionMessage.ToolCallResponse =>
          tcr.id() shouldBe "id1"
          tcr.name() shouldBe "render_chart"
          tcr.text() shouldBe "just text"
        case other => fail(s"expected a legacy ToolCallResponse, got [${other.getClass.getSimpleName}]")
      }
    }

    "keep a single image-URI content as a MultimodalToolCallResponse" in {
      val image = new SpiAgent.ImageUriMessageContent(
        URI.create("object://bucket/chart.png"),
        SpiAgent.ImageMessageContent.Auto,
        Some("image/png"))

      toSessionMessage(Seq(image)) match {
        case mtcr: SessionMessage.MultimodalToolCallResponse =>
          mtcr.contents().asScala.toList match {
            case (img: SessionMessage.MessageContent.ImageUriMessageContent) :: Nil =>
              img.uri() shouldBe "object://bucket/chart.png"
            case other => fail(s"unexpected contents: $other")
          }
        case other => fail(s"expected a MultimodalToolCallResponse, got [${other.getClass.getSimpleName}]")
      }
    }

    "keep a text + image content list as a MultimodalToolCallResponse in order" in {
      val text = new SpiAgent.TextMessageContent("caption")
      val image = new SpiAgent.ImageUriMessageContent(
        URI.create("object://bucket/chart.png"),
        SpiAgent.ImageMessageContent.Auto,
        Some("image/png"))

      toSessionMessage(Seq(text, image)) match {
        case mtcr: SessionMessage.MultimodalToolCallResponse =>
          mtcr.contents().asScala.toList match {
            case (t: SessionMessage.MessageContent.TextMessageContent) ::
                (img: SessionMessage.MessageContent.ImageUriMessageContent) :: Nil =>
              t.text() shouldBe "caption"
              img.uri() shouldBe "object://bucket/chart.png"
            case other => fail(s"unexpected contents: $other")
          }
        case other => fail(s"expected a MultimodalToolCallResponse, got [${other.getClass.getSimpleName}]")
      }
    }
  }
}
