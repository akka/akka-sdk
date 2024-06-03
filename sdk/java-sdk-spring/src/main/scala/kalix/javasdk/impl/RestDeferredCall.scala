/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl

import java.util.concurrent.CompletionStage
import kalix.javasdk.DeferredCall
import kalix.javasdk.Metadata

/**
 * INTERNAL API
 */
final case class RestDeferredCall[I, O](
    message: I,
    metadata: MetadataImpl,
    fullServiceName: String,
    methodName: String,
    asyncCall: Metadata => CompletionStage[O])
    extends DeferredCall[I, O] {

  override def invokeAsync(): CompletionStage[O] = asyncCall(metadata)

  override def withMetadata(metadata: Metadata): RestDeferredCall[I, O] = {
    this.copy(metadata = metadata.asInstanceOf[MetadataImpl])
  }
}
