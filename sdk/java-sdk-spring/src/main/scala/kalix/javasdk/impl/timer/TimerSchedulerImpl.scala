/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.timer

import java.time.Duration
import java.util.concurrent.CompletionStage
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters.FutureOps
import akka.Done
import akka.actor.ActorSystem
import akka.grpc.scaladsl.SingleResponseRequestBuilder
import com.google.protobuf.any.{ Any => ScalaPbAny }
import com.google.protobuf.duration.{ Duration => ProtoDuration }
import kalix.javasdk.DeferredCall
import kalix.javasdk.Metadata
import kalix.javasdk.impl.GrpcClients
import kalix.javasdk.impl.MessageCodec
import kalix.javasdk.impl.RestDeferredCall
import kalix.javasdk.impl.client.EmbeddedDeferredCall
import kalix.javasdk.timer.TimerScheduler
import kalix.timers.timers.Call
import kalix.timers.timers.SingleTimer
import kalix.timers.timers.TimerService
import kalix.timers.timers.TimerServiceClient

import scala.jdk.DurationConverters.JavaDurationOps

/** INTERNAL API */
private[kalix] final class TimerSchedulerImpl(
    val messageCodec: MessageCodec,
    val system: ActorSystem,
    val timerClient: kalix.javasdk.spi.TimerClient,
    val metadata: Metadata)
    extends TimerScheduler {

  override def startSingleTimer[I, O](
      name: String,
      delay: Duration,
      deferredCall: DeferredCall[I, O]): CompletionStage[Done] =
    startSingleTimer(name, delay, 0, deferredCall)

  override def startSingleTimer[I, O](
      name: String,
      delay: Duration,
      maxRetries: Int,
      deferredCall: DeferredCall[I, O]): CompletionStage[Done] = {

    deferredCall match {
      case restDeferredCall: RestDeferredCall[I, O] =>
        val timerServiceClient =
          GrpcClients(system).getProxyGrpcClient(classOf[TimerService]).asInstanceOf[TimerServiceClient]
        val call = Call(
          restDeferredCall.fullServiceName,
          restDeferredCall.methodName,
          Some(restDeferredCall.message.asInstanceOf[ScalaPbAny]))

        val singleTimer = SingleTimer(name, Some(call), Some(ProtoDuration(delay)), maxRetries)
        addHeaders(timerServiceClient.addSingle(), metadata).invoke(singleTimer).asJava.thenApply(_ => Done)
      case embeddedDeferredCall: EmbeddedDeferredCall[I, O] =>
        timerClient.startSingleTimer(name, delay.toScala, maxRetries, embeddedDeferredCall.deferredRequest()).asJava

      case unknown =>
        // should never happen, but needs to make compiler happy
        throw new IllegalStateException(s"Unknown DeferredCall implementation: $unknown")
    }

  }

  def cancel(name: String): CompletionStage[Done] = {
    timerClient.removeTimer(name).asJava
  }

  private def addHeaders[I, O](
      callBuilder: SingleResponseRequestBuilder[I, O],
      metadata: Metadata): SingleResponseRequestBuilder[I, O] = {
    metadata.asScala.foldLeft(callBuilder) { case (builder, entry) =>
      if (entry.isText) builder.addHeader(entry.getKey, entry.getValue)
      else builder
    }
  }

}
