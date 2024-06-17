/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.client

import akka.annotation.InternalApi
import akka.http.scaladsl.model.ContentTypes
import akka.util.ByteString
import kalix.javasdk.DeferredCall
import kalix.javasdk.JsonSupport
import kalix.javasdk.Metadata
import kalix.javasdk.impl.MetadataImpl
import kalix.javasdk.impl.MetadataImpl.toProtocol
import kalix.javasdk.spi.ComponentType
import kalix.javasdk.spi.DeferredRequest

import java.util.concurrent.CompletionStage

/**
 * INTERNAL API
 */
@InternalApi
final case class EmbeddedDeferredCall[I, O](
    message: I,
    metadata: MetadataImpl,
    componentType: ComponentType,
    fullServiceName: String,
    methodName: String,
    entityId: Option[String],
    asyncCall: Metadata => CompletionStage[O])
    extends DeferredCall[I, O] {

  override def invokeAsync(): CompletionStage[O] = asyncCall(metadata)

  override def withMetadata(metadata: Metadata): EmbeddedDeferredCall[I, O] = {
    this.copy(metadata = metadata.asInstanceOf[MetadataImpl])
  }

  def deferredRequest(): DeferredRequest = DeferredRequest(
    componentType,
    fullServiceName,
    methodName = methodName,
    entityId = entityId,
    contentType = ContentTypes.`application/json`,
    payload =
      if (message == null) ByteString.empty
      else JsonSupport.encodeToAkkaByteString(message),
    metadata = toProtocol(metadata).getOrElse(kalix.protocol.component.Metadata.defaultInstance))

}
