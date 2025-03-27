/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.util
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.stream

import scala.jdk.DurationConverters.ScalaDurationOps

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk
import akka.javasdk.AsyncUtils
import akka.pattern.Patterns

@InternalApi
private[akka] class AsyncUtilsImpl(actorSystem: ActorSystem) extends AsyncUtils {

  override def retry[T](call: Callable[CompletionStage[T]], attempts: Int): CompletionStage[T] = {
    retry(call, RetrySettingsBuilder(attempts).withBackoff())
  }

  override def retry[T](
      call: Callable[CompletionStage[T]],
      retrySettings: javasdk.RetrySettings): CompletionStage[T] = {
    retrySettings.asInstanceOf[RetrySettings] match {
      case RetrySettings.FixedDelayRetrySettings(attempts, fixedDelay) =>
        Patterns.retry(call, attempts, fixedDelay.toJava, actorSystem)
      case RetrySettings.BackoffRetrySettings(attempts, minBackoff, maxBackoff, randomFactor) =>
        Patterns.retry(call, attempts, minBackoff.toJava, maxBackoff.toJava, randomFactor, actorSystem)
    }
  }

  override def sequence[T](completableFutures: util.List[CompletableFuture[T]]): CompletableFuture[util.List[T]] = {
    val completableFuturesArray =
      completableFutures.toArray(new Array[CompletableFuture[T]](completableFutures.size))

    val whenAllCompleted: CompletableFuture[Void] = CompletableFuture.allOf(completableFuturesArray: _*)

    whenAllCompleted.thenApply(_ => {
      val resultStream: stream.Stream[T] = completableFutures.stream.map(future => future.join())
      resultStream.toList
    })
  }
}
