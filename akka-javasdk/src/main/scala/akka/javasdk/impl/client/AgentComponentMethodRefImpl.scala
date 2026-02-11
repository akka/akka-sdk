/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.util.concurrent.CompletionStage

import akka.NotUsed
import akka.annotation.InternalApi
import akka.javasdk.DeferredCall
import akka.javasdk.Metadata
import akka.javasdk.agent.Agent
import akka.javasdk.client.AgentComponentMethodRef
import akka.javasdk.client.AgentComponentMethodRef1
import akka.javasdk.client.ComponentMethodRef
import akka.javasdk.client.ComponentMethodRef1
import akka.javasdk.client.DynamicMethodRef
import akka.pattern.RetrySettings

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class AgentComponentMethodRefImpl[A1, R](componentMethodRefImpl: ComponentMethodRefImpl[A1, R])
    extends ComponentMethodRef[R]
    with AgentComponentMethodRef[R]
    with AgentComponentMethodRef1[A1, R]
    with ComponentMethodRef1[A1, R]
    with DynamicMethodRef[A1, R] {

  override def withMetadata(metadata: Metadata): ComponentMethodRefImpl[A1, R] =
    componentMethodRefImpl.withMetadata(metadata)

  override def withRetry(retrySettings: RetrySettings): ComponentMethodRefImpl[A1, R] =
    componentMethodRefImpl.withRetry(retrySettings)

  override def withRetry(maxRetries: Int): ComponentMethodRefImpl[A1, R] = componentMethodRefImpl.withRetry(maxRetries)

  override def invokeAsync(arg: A1): CompletionStage[R] = componentMethodRefImpl.invokeAsync(arg)

  override def invoke(arg: A1): R = componentMethodRefImpl.invoke(arg)

  override def invokeAsync(): CompletionStage[R] = componentMethodRefImpl.invokeAsync()

  override def invoke(): R = componentMethodRefImpl.invoke()

  override def deferred(): DeferredCall[NotUsed, R] = componentMethodRefImpl.deferred()

  override def deferred(arg: A1): DeferredCall[A1, R] = componentMethodRefImpl.deferred(arg)

  override def invokeReply(): Agent.AgentReply[R] = {
    val callResult = componentMethodRefImpl.invokeWithMetadata()
    new Agent.AgentReply[R](callResult.value, toTokenUsage(callResult.metadata))
  }

  private def toTokenUsage(metadata: Metadata): Agent.TokenUsage = {
    val input = metadata.get("input_tokens").map[Integer](_.toInt).orElse(0)
    val output = metadata.get("output_tokens").map[Integer](_.toInt).orElse(0)
    new Agent.TokenUsage(input, output)
  }

  override def invokeReply(arg: A1): Agent.AgentReply[R] = {
    val callResult = componentMethodRefImpl.invokeWithMetadata(arg)
    new Agent.AgentReply[R](callResult.value, toTokenUsage(callResult.metadata))
  }
}
