/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.util.concurrent.CompletionStage

import akka.NotUsed
import akka.annotation.InternalApi
import akka.javasdk.DeferredCall
import akka.javasdk.Metadata
import akka.javasdk.client.ComponentInvokeOnlyMethodRef
import akka.javasdk.client.ComponentInvokeOnlyMethodRef1
import akka.javasdk.client.ComponentMethodRef
import akka.javasdk.client.ComponentMethodRef1
import akka.javasdk.client.DynamicMethodRef
import akka.javasdk.impl.ErrorHandling.unwrapExecutionExceptionCatcher
import akka.pattern.RetrySettings

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class ComponentMethodRefImpl[A1, R](
    optionalId: Option[String],
    metadataOpt: Option[Metadata],
    createDeferred: (Option[Metadata], Option[RetrySettings], Option[A1]) => DeferredCall[A1, R],
    canBeDeferred: Boolean = true,
    retrySettings: Option[RetrySettings] = None)
    extends ComponentMethodRef[R]
    with ComponentMethodRef1[A1, R]
    with ComponentInvokeOnlyMethodRef[R]
    with ComponentInvokeOnlyMethodRef1[A1, R]
    with DynamicMethodRef[A1, R] {

  override def withMetadata(metadata: Metadata): ComponentMethodRefImpl[A1, R] = {
    val merged = metadataOpt.map[Metadata](m => m.merge(metadata)).getOrElse(metadata)
    copy(metadataOpt = Some(merged))
  }

  override def withRetry(retrySettings: RetrySettings): ComponentMethodRefImpl[A1, R] = {
    copy(retrySettings = Some(retrySettings))
  }

  override def withRetry(maxRetries: Int): ComponentMethodRefImpl[A1, R] = {
    copy(retrySettings = Some(RetrySettings(maxRetries)))
  }

  def deferred(): DeferredCall[NotUsed, R] = {
    // extra protection against type cast since the same backing impl for non deferrable and deferrable
    if (!canBeDeferred) throw new IllegalStateException("Call to this method cannot be deferred")
    createDeferred(metadataOpt, None, None).asInstanceOf[DeferredCall[NotUsed, R]]
  }

  def invokeAsync(): CompletionStage[R] = {
    createDeferred(metadataOpt, retrySettings, None).asInstanceOf[DeferredCallImpl[NotUsed, R]].invokeAsync()
  }

  def deferred(arg: A1): DeferredCall[A1, R] = {
    // extra protection against type cast since the same backing impl for non deferrable and deferrable
    if (!canBeDeferred)
      throw new IllegalStateException("Call to this method cannot be deferred")
    if (arg == null)
      throw new IllegalStateException("Argument to deferred must not be null")

    createDeferred(metadataOpt, None, Some(arg))
  }

  def invokeAsync(arg: A1): CompletionStage[R] = {
    if (arg == null)
      throw new IllegalStateException("Argument to invokeAsync must not be null")
    createDeferred(metadataOpt, retrySettings, Some(arg)).asInstanceOf[DeferredCallImpl[NotUsed, R]].invokeAsync()
  }

  // Note: invoke/ask timeout handled by runtime so no timeout needed here
  override def invoke(): R =
    try {
      invokeAsync().toCompletableFuture.get()
    } catch unwrapExecutionExceptionCatcher

  override def invoke(arg: A1): R = {
    try {
      invokeAsync(arg).toCompletableFuture.get()
    } catch unwrapExecutionExceptionCatcher
  }
}
