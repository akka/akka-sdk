/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.util.concurrent.CompletionStage

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.client.AgentInvokeOnlyMethodRef
import akka.javasdk.client.AgentInvokeOnlyMethodRef1
import akka.javasdk.client.DynamicMethodRef
import akka.pattern.RetrySettings

/**
 * INTERNAL API
 */
@InternalApi
private[impl] case class AgentInvokeOnlyMethodRefImpl[A1, R](componentMethodRefImpl: ComponentMethodRefImpl[A1, R])
    extends AgentInvokeOnlyMethodRef[R]
    with AgentInvokeOnlyMethodRef1[A1, R]
    with DynamicMethodRef[A1, R] {

  override def invoke(): R = {
    componentMethodRefImpl.invoke()
  }

  override def invoke(arg: A1): R = {
    componentMethodRefImpl.invoke(arg)
  }

  override def invokeAsync(): CompletionStage[R] = {
    componentMethodRefImpl.invokeAsync()
  }

  override def invokeAsync(arg: A1): CompletionStage[R] = {
    componentMethodRefImpl.invokeAsync(arg)
  }

  override def withDetailedReply(): AgentInvokeReplyOnlyMethodRefImpl[A1, R] =
    AgentInvokeReplyOnlyMethodRefImpl[A1, R](componentMethodRefImpl)

  override def withMetadata(metadata: Metadata): DynamicMethodRef[A1, R] =
    componentMethodRefImpl.withMetadata(metadata)

  override def withRetry(retrySettings: RetrySettings): DynamicMethodRef[A1, R] =
    componentMethodRefImpl.withRetry(retrySettings)

  override def withRetry(maxRetries: Int): DynamicMethodRef[A1, R] =
    componentMethodRefImpl.withRetry(maxRetries)
}
