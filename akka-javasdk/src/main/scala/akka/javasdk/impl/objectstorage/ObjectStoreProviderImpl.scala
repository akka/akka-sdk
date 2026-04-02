/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.objectstorage

import java.util
import java.util.Optional
import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.jdk.javaapi.FutureConverters
import scala.jdk.javaapi.OptionConverters

import akka.Done
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.http.javadsl.model.ContentType
import akka.http.javadsl.{ model => jm }
import akka.http.scaladsl.{ model => sm }
import akka.javasdk.objectstorage.ObjectMetadata
import akka.javasdk.objectstorage.ObjectStorage
import akka.javasdk.objectstorage.ObjectStorageProvider
import akka.javasdk.objectstorage.StorageObject
import akka.runtime.sdk.spi.SpiObjectStorage
import akka.runtime.sdk.spi.SpiObjectStorageClient
import akka.runtime.sdk.spi.{ ObjectMetadata => SpiObjectMetadata }
import akka.stream.javadsl.Sink
import akka.stream.javadsl.{ Source => JSource }
import akka.util.ByteString

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class ObjectStorageProviderImpl(spiObjectStorage: SpiObjectStorage, system: ActorSystem[_])
    extends ObjectStorageProvider {
  override def forBucket(bucket: String): ObjectStorage =
    new ObjectStorageImpl(bucket, spiObjectStorage.client(bucket), system)
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class ObjectStorageImpl(bucket: String, spiClient: SpiObjectStorageClient, system: ActorSystem[_])
    extends ObjectStorage {

  private implicit val ec: ExecutionContext = system.executionContext

  override def bucketName(): String = bucket

  private def toPublicMetadata(m: SpiObjectMetadata): ObjectMetadata =
    new ObjectMetadata(
      m.key,
      m.size,
      // scaladsl.ContentType extends javadsl.ContentType, safe to cast
      OptionConverters.toJava(m.contentType).map(_.asInstanceOf[jm.ContentType]),
      OptionConverters.toJava(m.eTag),
      m.lastModified)

  private def toSpiContentType(ct: jm.ContentType): sm.ContentType =
    // All javadsl.ContentType instances are scaladsl.ContentType at runtime
    ct.asInstanceOf[sm.ContentType]

  // ── Async implementations ────────────────────────────────────────────────

  override def getAsync(key: String): CompletionStage[Optional[StorageObject]] =
    FutureConverters.asJava(
      spiClient
        .get(key)
        .map(opt => OptionConverters.toJava(opt.map(so => new StorageObject(toPublicMetadata(so.metadata), so.data)))))

  override def putAsync(key: String, data: ByteString): CompletionStage[Done] =
    FutureConverters.asJava(spiClient.put(key, data, None, Map.empty))

  override def putAsync(key: String, data: ByteString, contentType: jm.ContentType): CompletionStage[Done] =
    FutureConverters.asJava(spiClient.put(key, data, Some(toSpiContentType(contentType)), Map.empty))

  override def putAsync(
      key: String,
      data: ByteString,
      contentType: ContentType,
      metadata: util.Map[String, String]): CompletionStage[Done] =
    FutureConverters.asJava(spiClient.put(key, data, Some(toSpiContentType(contentType)), metadata.asScala.toMap))

  override def deleteAsync(key: String): CompletionStage[Done] =
    FutureConverters.asJava(spiClient.delete(key))

  override def getMetadataAsync(key: String): CompletionStage[Optional[ObjectMetadata]] =
    FutureConverters.asJava(spiClient.metadata(key).map(opt => OptionConverters.toJava(opt.map(toPublicMetadata))))

  // ── Blocking (Loom-friendly) API — delegates to async ───────────────────

  override def get(key: String): Optional[StorageObject] =
    getAsync(key).toCompletableFuture.get()

  override def put(key: String, data: ByteString): Done =
    putAsync(key, data).toCompletableFuture.get()

  override def put(key: String, data: ByteString, contentType: jm.ContentType): Done =
    putAsync(key, data, contentType).toCompletableFuture.get()

  override def put(key: String, data: ByteString, contentType: ContentType, metadata: util.Map[String, String]): Done =
    putAsync(key, data, contentType, metadata).toCompletableFuture.get()

  override def delete(key: String): Done =
    deleteAsync(key).toCompletableFuture.get()

  override def getMetadata(key: String): Optional[ObjectMetadata] =
    getMetadataAsync(key).toCompletableFuture.get()

  override def list(prefix: String): util.List[ObjectMetadata] =
    streamList(prefix).runWith(Sink.seq[ObjectMetadata], system).toCompletableFuture.get()

  override def list(): util.List[ObjectMetadata] = list("")
  // ── Streaming ────────────────────────────────────────────────────────────

  override def streamList(prefix: String): JSource[ObjectMetadata, NotUsed] =
    spiClient.list(prefix).map(toPublicMetadata).asJava

  override def streamList(): JSource[ObjectMetadata, NotUsed] = streamList("")

  override def getStreamAsync(key: String): CompletionStage[Optional[JSource[ByteString, NotUsed]]] =
    FutureConverters.asJava(spiClient.getStream(key).map(opt => OptionConverters.toJava(opt.map(_.asJava))))

  override def putStreamAsync(key: String, data: JSource[ByteString, _]): CompletionStage[Done] =
    FutureConverters.asJava(spiClient.putStream(key, data.asScala, None, Map.empty))

  override def putStreamAsync(
      key: String,
      data: JSource[ByteString, _],
      contentType: jm.ContentType): CompletionStage[Done] =
    FutureConverters.asJava(spiClient.putStream(key, data.asScala, Some(toSpiContentType(contentType)), Map.empty))

  override def putStreamAsync(
      key: String,
      data: JSource[ByteString, _],
      contentType: ContentType,
      metadata: util.Map[String, String]): CompletionStage[Done] =
    FutureConverters.asJava(spiClient.putStream(key, data.asScala, None, metadata.asScala.toMap))

}
