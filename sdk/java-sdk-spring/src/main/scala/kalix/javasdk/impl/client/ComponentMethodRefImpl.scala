/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.client

import akka.NotUsed
import kalix.javasdk.DeferredCall
import akka.annotation.InternalApi
import kalix.javasdk.Metadata
import kalix.javasdk.client.ComponentInvokeOnlyMethodRef
import kalix.javasdk.client.ComponentInvokeOnlyMethodRef1
import kalix.javasdk.client.ComponentMethodRef
import kalix.javasdk.client.ComponentMethodRef1

import java.util.concurrent.CompletionStage

/**
 * INTERNAL API
 */
@InternalApi
final case class ComponentMethodRefImpl[A1, R](
    optionalId: Option[String],
    metadataOpt: Option[Metadata],
    createDeferred: (Option[Metadata], Option[A1]) => DeferredCall[A1, R],
    canBeDeferred: Boolean = true)
    extends ComponentMethodRef[R]
    with ComponentMethodRef1[A1, R]
    with ComponentInvokeOnlyMethodRef[R]
    with ComponentInvokeOnlyMethodRef1[A1, R] {

  override def withMetadata(metadata: Metadata): ComponentMethodRefImpl[A1, R] = {
    val merged = metadataOpt.map[Metadata](m => m.merge(metadata)).getOrElse(metadata)
    copy(metadataOpt = Some(merged))
  }

  def deferred(): DeferredCall[NotUsed, R] = {
    // extra protection against type cast since the same backing impl for non deferrable and deferrable
    if (!canBeDeferred) throw new IllegalStateException("Call to this method cannot be deferred")
    createDeferred(metadataOpt, None).asInstanceOf[DeferredCall[NotUsed, R]]
  }

  def invokeAsync(): CompletionStage[R] =
    createDeferred(metadataOpt, None).asInstanceOf[DeferredCall[NotUsed, R]].invokeAsync()

  def deferred(arg: A1): DeferredCall[A1, R] = {
    // extra protection against type cast since the same backing impl for non deferrable and deferrable
    if (!canBeDeferred) throw new IllegalStateException("Call to this method cannot be deferred")
    createDeferred(metadataOpt, Some(arg))
  }

  def invokeAsync(arg: A1): CompletionStage[R] =
    createDeferred(metadataOpt, Some(arg)).invokeAsync()

}
