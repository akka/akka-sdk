/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.util.concurrent.CompletionStage

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.agent.Agent
import akka.javasdk.client.AgentReplyInvokeOnlyMethodRef
import akka.javasdk.client.AgentReplyInvokeOnlyMethodRef1
import akka.javasdk.impl.ErrorHandling.unwrapExecutionExceptionCatcher
import akka.runtime.sdk.spi.SpiAgent

/**
 * INTERNAL API
 */
@InternalApi
private[impl] case class AgentInvokeReplyOnlyMethodRefImpl[A1, R](componentMethodRefImpl: ComponentMethodRefImpl[A1, R])
    extends AgentReplyInvokeOnlyMethodRef[R]
    with AgentReplyInvokeOnlyMethodRef1[A1, R] {

  override def invoke(): Agent.AgentReply[R] = {
    try {
      invokeAsync().toCompletableFuture.get()
    } catch unwrapExecutionExceptionCatcher
  }

  override def invoke(arg: A1): Agent.AgentReply[R] = {
    try {
      invokeAsync(arg).toCompletableFuture.get()
    } catch unwrapExecutionExceptionCatcher
  }

  override def invokeAsync(): CompletionStage[Agent.AgentReply[R]] = {
    componentMethodRefImpl.callComponent().thenApply { callResult =>
      new Agent.AgentReply[R](callResult.value, toTokenUsage(callResult.metadata))
    }
  }

  override def invokeAsync(arg: A1): CompletionStage[Agent.AgentReply[R]] = {
    componentMethodRefImpl.callComponent(arg).thenApply { callResult =>
      new Agent.AgentReply[R](callResult.value, toTokenUsage(callResult.metadata))
    }
  }

  private def toTokenUsage(metadata: Metadata): Agent.TokenUsage = {
    val input = metadata.get(SpiAgent.AgentInputTokensKey).map[Integer](_.toInt).orElse(0)
    val output = metadata.get(SpiAgent.AgentOutputTokensKey).map[Integer](_.toInt).orElse(0)
    new Agent.TokenUsage(input, output)
  }
}
