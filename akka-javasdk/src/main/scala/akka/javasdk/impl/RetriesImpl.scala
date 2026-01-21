/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.Retries
import akka.javasdk.impl.ErrorHandling.unwrapExecutionExceptionCatcher
import akka.pattern.Patterns
import akka.pattern.RetrySettings

@InternalApi
private[akka] class RetriesImpl(actorSystem: ActorSystem) extends Retries {

  override def retryAsync[T](call: Callable[CompletionStage[T]], maxRetries: Int): CompletionStage[T] = {
    retryAsync(call, RetrySettings(maxRetries))
  }

  override def retryAsync[T](call: Callable[CompletionStage[T]], maxRetries: RetrySettings): CompletionStage[T] = {
    Patterns.retry(call, maxRetries, actorSystem)
  }

  override def retry[T](call: Callable[T], maxRetries: Int): T = {
    retry(call, RetrySettings(maxRetries))
  }

  override def retry[T](callable: Callable[T], retrySettings: RetrySettings): T = {
    try {
      retryAsync(
        new Callable[CompletionStage[T]] {
          override def call(): CompletionStage[T] = {
            CompletableFuture.completedFuture(callable.call())
          }
        },
        retrySettings).toCompletableFuture
        .get()
    } catch unwrapExecutionExceptionCatcher
  }
}
