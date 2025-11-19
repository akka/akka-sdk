/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import java.time.Duration
import java.util

import scala.concurrent.Await
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.jdk.DurationConverters.JavaDurationOps

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.http.javadsl.model.HttpMethod
import akka.http.javadsl.model.sse.ServerSentEvent
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaRange
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpMethod => ScalaHttpMethod }
import akka.http.scaladsl.unmarshalling.sse.EventStreamParser
import akka.javasdk.testkit.SseRouteTester
import akka.stream.scaladsl.Sink

/**
 * INTERNAL API Used by the testkit
 */
@InternalApi
private[testkit] final class SseRouteTesterImpl(runtimeHost: String, runtimePort: Int)(implicit system: ActorSystem[_])
    extends SseRouteTester {
  private val acceptHeader = Accept(MediaRange(MediaType.text("event-stream")))

  private def consumeN(
      path: String,
      method: HttpMethod,
      timeout: Duration,
      count: Int,
      additionalHeaders: Seq[HttpHeader] = Seq.empty): Seq[ServerSentEvent] = {
    val url = s"http://$runtimeHost:$runtimePort/$path"

    val response = Await.result(
      Http(system)
        .singleRequest(
          HttpRequest(
            uri = url,
            method = method.asInstanceOf[ScalaHttpMethod],
            headers = Seq(acceptHeader) ++ additionalHeaders)),
      timeout.toScala)

    val futureSeq = response.entity.dataBytes
      .via(EventStreamParser.apply(16384, 16384))
      .map(evt => evt: ServerSentEvent) // Scala
      .take(count)
      .runWith(Sink.seq)

    Await.result(futureSeq, timeout.toScala)
  }

  override def receiveFirstN(
      path: String,
      method: HttpMethod,
      count: Int,
      timeout: Duration): util.List[ServerSentEvent] =
    consumeN(path, method, timeout, count).asJava

  override def receiveNFromOffset(
      path: String,
      method: HttpMethod,
      count: Int,
      startFromId: String,
      timeout: Duration): util.List[ServerSentEvent] =
    consumeN(path, method, timeout, count, Seq(RawHeader("Last-Event-ID", startFromId))).asJava
}
