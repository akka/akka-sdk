/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import java.time.Instant
import java.util.Optional

import scala.jdk.OptionConverters.RichOptional

import akka.NotUsed
import akka.annotation.InternalApi
import akka.javasdk.client.ViewStreamMethodRef
import akka.javasdk.client.ViewStreamMethodRef1
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.client.ViewClientImpl.ViewMethodProperties
import akka.javasdk.impl.client.ViewClientImpl.encodeArgument
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.view.EntryWithMetadata
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiMetadataEntry
import akka.runtime.sdk.spi.ViewRequest
import akka.runtime.sdk.spi.ViewResult
import akka.runtime.sdk.spi.{ ViewClient => RuntimeViewClient }
import akka.stream.javadsl.Source

/**
 * INTERNAL API
 */
@InternalApi
private[impl] trait AbstractViewStreamMethodRef {

  protected def viewClient: RuntimeViewClient
  protected def serializer: JsonSerializer
  protected def viewMethodProperties: ViewMethodProperties

  protected def invoke(params: Option[Any], updatedAfter: Option[Instant]): Source[ViewResult, NotUsed] =
    viewClient
      .queryStream(
        new ViewRequest(
          viewMethodProperties.componentId,
          viewMethodProperties.methodName,
          encodeArgument(serializer, viewMethodProperties.method, params),
          updatedAfter match {
            case Some(instant) =>
              new SpiMetadata(Vector(new SpiMetadataEntry("starting-offset", instant.toString))) // ISO-8601 instant
            case None => SpiMetadata.empty
          }))
      .asJava

  protected def parse[R](viewResult: ViewResult): R =
    // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
    serializer.fromBytes(viewMethodProperties.queryReturnType.asInstanceOf[Class[R]], viewResult.payload)

  protected def parseWithMetadata[R](viewResult: ViewResult): EntryWithMetadata[R] =
    // Note: not Kalix JSON encoded here, regular/normal utf8 bytes
    new EntryWithMetadata(
      serializer.fromBytes(viewMethodProperties.queryReturnType.asInstanceOf[Class[R]], viewResult.payload),
      MetadataImpl.of(viewResult.metadata))

}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class ViewStreamMethodRefImpl[R](
    override val viewClient: RuntimeViewClient,
    override val serializer: JsonSerializer,
    override val viewMethodProperties: ViewMethodProperties)
    extends AbstractViewStreamMethodRef
    with ViewStreamMethodRef[R] {

  override def source(): Source[R, NotUsed] = invoke(None, None).map(parse[R])
  override def entriesSource(): Source[EntryWithMetadata[R], NotUsed] = invoke(None, None).map(parseWithMetadata[R])
  override def entriesSource(updatedAfter: Optional[Instant]): Source[EntryWithMetadata[R], NotUsed] =
    invoke(None, updatedAfter.toScala).map(parseWithMetadata[R])
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class ViewStreamMethodRefImpl1[A1, R](
    override val viewClient: RuntimeViewClient,
    override val serializer: JsonSerializer,
    override val viewMethodProperties: ViewMethodProperties)
    extends AbstractViewStreamMethodRef
    with ViewStreamMethodRef1[A1, R] {

  override def source(arg: A1): Source[R, NotUsed] = invoke(Some(arg), None).map(parse[R])
  override def entriesSource(arg: A1): Source[EntryWithMetadata[R], NotUsed] =
    invoke(Some(arg), None).map(parseWithMetadata[R])
  override def entriesSource(arg: A1, updatedAfter: Optional[Instant]): Source[EntryWithMetadata[R], NotUsed] =
    invoke(Some(arg), updatedAfter.toScala).map(parseWithMetadata[R])
}
