/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.Optional

import akka.javasdk.agent.MessageContent
import akka.runtime.sdk.spi.SpiAgent
import akka.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AgentImplMessageContentSpec extends AnyWordSpec with Matchers {

  private def imageBytes(bytes: ByteString, mimeType: String): MessageContent.ImageBytesMessageContent =
    AgentImpl.fromSpiMessageContent(
      new SpiAgent.ImageBytesMessageContent(bytes, mimeType, SpiAgent.ImageMessageContent.Auto)) match {
      case image: MessageContent.ImageBytesMessageContent => image
      case other => fail(s"expected image bytes, got [${other.getClass.getName}]")
    }

  "AgentImpl.fromSpiMessageContent" should {

    "keep the mime type of an image" in {
      imageBytes(ByteString("image"), "image/png").mimeType() shouldBe Optional.of("image/png")
    }

    "give no mime type for an image with an empty mime type" in {
      imageBytes(ByteString("image"), "").mimeType() shouldBe Optional.empty()
    }

    "give the image bytes without copying them" in {
      val data = "image".getBytes

      imageBytes(ByteString.fromArrayUnsafe(data), "image/png").data() shouldBe theSameInstanceAs(data)
    }

    "give the PDF bytes without copying them" in {
      val data = "pdf".getBytes

      AgentImpl.fromSpiMessageContent(new SpiAgent.PdfBytesMessageContent(ByteString.fromArrayUnsafe(data))) match {
        case pdf: MessageContent.PdfBytesMessageContent => pdf.data() shouldBe theSameInstanceAs(data)
        case other                                      => fail(s"expected PDF bytes, got [${other.getClass.getName}]")
      }
    }
  }
}
