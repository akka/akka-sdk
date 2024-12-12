/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.util.concurrent.CompletionStage

import akka.annotation.InternalApi
import akka.javasdk.DeferredCall
import akka.javasdk.Metadata
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.MetadataImpl.toProtocol
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.ComponentType
import akka.runtime.sdk.spi.DeferredRequest

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class DeferredCallImpl[I, O](
    message: I,
    metadata: MetadataImpl,
    componentType: ComponentType,
    componentId: String,
    methodName: String,
    entityId: Option[String],
    asyncCall: Metadata => CompletionStage[O],
    serializer: JsonSerializer)
    extends DeferredCall[I, O] {

  def invokeAsync(): CompletionStage[O] = asyncCall(metadata)

  override def withMetadata(metadata: Metadata): DeferredCallImpl[I, O] = {
    this.copy(metadata = metadata.asInstanceOf[MetadataImpl])
  }

  def deferredRequest(): DeferredRequest = {
    val payload =
      if (message == null)
        BytesPayload.empty
      else
        serializer.toBytes(message)

    new DeferredRequest(
      componentType,
      componentId,
      methodName = methodName,
      entityId = entityId,
      payload = payload,
      metadata = toProtocol(metadata).getOrElse(kalix.protocol.component.Metadata.defaultInstance))
  }

}
