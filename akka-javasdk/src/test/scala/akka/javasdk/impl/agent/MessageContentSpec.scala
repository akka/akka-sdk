/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.javasdk.agent.MessageContent.BucketRef
import akka.javasdk.agent.MessageContent.ImageUrlMessageContent
import akka.javasdk.agent.MessageContent.PdfUrlMessageContent
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MessageContentSpec extends AnyWordSpec with Matchers {

  private val bucket: BucketRef = () => "my-bucket"

  "MessageContent.ImageUrlMessageContent.create" should {

    "build an object:// URI from a BucketRef and key" in {
      val content = ImageUrlMessageContent.create(bucket, "images/photo.jpg")
      content.uri().toString shouldBe "object://my-bucket/images/photo.jpg"
    }
  }

  "MessageContent.PdfUrlMessageContent.create" should {

    "build an object:// URI from a BucketRef and key" in {
      val content = PdfUrlMessageContent.create(bucket, "docs/report.pdf")
      content.uri().toString shouldBe "object://my-bucket/docs/report.pdf"
    }
  }
}
