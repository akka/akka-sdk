/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.{Done, NotUsed}
import akka.http.javadsl.model.ContentType
import akka.javasdk.agent.MessageContent.ImageUrlMessageContent
import akka.javasdk.agent.MessageContent.PdfUrlMessageContent
import akka.javasdk.objectstorage.{ObjectMetadata, ObjectStore, StoreObject}
import akka.stream.javadsl.Source
import akka.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util
import java.util.Optional
import java.util.concurrent.CompletionStage

class MessageContentSpec extends AnyWordSpec with Matchers {

  private val bucket = new ObjectStore {
    override def bucketName(): String = "my-bucket"
    override def get(key: String): Optional[StoreObject] = ???
    override def put(key: String, data: ByteString): Done = ???
    override def put(key: String, data: ByteString, contentType: ContentType): Done = ???
    override def delete(key: String): Done = ???
    override def list(prefix: String): util.List[ObjectMetadata] = ???
    override def list(): util.List[ObjectMetadata] = ???
    override def getMetadata(key: String): Optional[ObjectMetadata] = ???
    override def streamList(prefix: String): Source[ObjectMetadata, NotUsed] = ???
    override def streamList(): Source[ObjectMetadata, NotUsed] = ???
    override def getStreamAsync(key: String): CompletionStage[Optional[Source[ByteString, NotUsed]]] = ???
    override def putStreamAsync(key: String, data: Source[ByteString, _]): CompletionStage[Done] = ???
    override def putStreamAsync(key: String, data: Source[ByteString, _], contentType: ContentType): CompletionStage[Done] = ???
    override def getAsync(key: String): CompletionStage[Optional[StoreObject]] = ???
    override def putAsync(key: String, data: ByteString): CompletionStage[Done] = ???
    override def putAsync(key: String, data: ByteString, contentType: ContentType): CompletionStage[Done] = ???
    override def deleteAsync(key: String): CompletionStage[Done] = ???
    override def getMetadataAsync(key: String): CompletionStage[Optional[ObjectMetadata]] = ???
  }

  "MessageContent.ImageUrlMessageContent.create" should {

    "build an object:// URI from a ObjectStore and key" in {
      val content = ImageUrlMessageContent.create(bucket, "images/photo.jpg")
      content.uri().toString shouldBe "object://my-bucket/images/photo.jpg"
    }
  }

  "MessageContent.PdfUrlMessageContent.create" should {

    "build an object:// URI from a ObjectStore and key" in {
      val content = PdfUrlMessageContent.create(bucket, "docs/report.pdf")
      content.uri().toString shouldBe "object://my-bucket/docs/report.pdf"
    }
  }
}
