/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.objectstorage

import scala.concurrent.Future

import akka.Done
import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.model.ContentType
import akka.javasdk.agent.MessageContent.ImageUrlMessageContent
import akka.javasdk.agent.MessageContent.PdfUrlMessageContent
import akka.runtime.sdk.spi.ObjectMetadata
import akka.runtime.sdk.spi.SpiObjectStoreClient
import akka.runtime.sdk.spi.StoreObject
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ObjectStoreImplSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  private val store = new ObjectStoreImpl("my-bucket", stubSpiClient, testKit.system)

  "ObjectStoreImpl" should {

    "create image content with object:// URL from key" in {
      val content = store.asImageContent("images/photo.jpg")
      content shouldBe an[ImageUrlMessageContent]
      content.asInstanceOf[ImageUrlMessageContent].uri().toString shouldBe "object://my-bucket/images/photo.jpg"
    }

    "create PDF content with object:// URL from key" in {
      val content = store.asPdfContent("docs/report.pdf")
      content shouldBe an[PdfUrlMessageContent]
      content.asInstanceOf[PdfUrlMessageContent].uri().toString shouldBe "object://my-bucket/docs/report.pdf"
    }
  }

  private def stubSpiClient: SpiObjectStoreClient = new SpiObjectStoreClient {
    def get(key: String): Future[Option[StoreObject]] = ???
    def put(key: String, data: ByteString, contentType: Option[ContentType]): Future[Done] = ???
    def delete(key: String): Future[Done] = ???
    def metadata(key: String): Future[Option[ObjectMetadata]] = ???
    def list(prefix: String): Source[ObjectMetadata, NotUsed] = ???
    def getStream(key: String): Future[Option[Source[ByteString, NotUsed]]] = ???
    def putStream(key: String, data: Source[ByteString, Any], contentType: Option[ContentType]): Future[Done] = ???
  }
}
