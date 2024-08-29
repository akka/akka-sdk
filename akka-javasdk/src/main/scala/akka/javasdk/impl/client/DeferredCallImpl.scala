/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import akka.annotation.InternalApi
import akka.http.scaladsl.model.ContentTypes
import akka.javasdk.impl.MetadataImpl
import akka.util.ByteString
import MetadataImpl.toProtocol
import akka.javasdk.DeferredCall
import akka.javasdk.JsonSupport
import akka.javasdk.Metadata
import akka.runtime.sdk.spi.ComponentType
import akka.runtime.sdk.spi.DeferredRequest

import java.util.concurrent.CompletionStage

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class DeferredCallImpl[I, O](
    message: I,
    metadata: MetadataImpl,
    componentType: ComponentType,
    fullServiceName: String,
    methodName: String,
    entityId: Option[String],
    asyncCall: Metadata => CompletionStage[O])
    extends DeferredCall[I, O] {

  def invokeAsync(): CompletionStage[O] = asyncCall(metadata)

  override def withMetadata(metadata: Metadata): DeferredCallImpl[I, O] = {
    this.copy(metadata = metadata.asInstanceOf[MetadataImpl])
  }

  def deferredRequest(): DeferredRequest = new DeferredRequest(
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
