/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.http.javadsl.Http
import akka.http.javadsl.model.ws.BinaryMessage
import akka.http.javadsl.model.ws.Message
import akka.http.javadsl.model.ws.TextMessage
import akka.http.javadsl.model.ws.WebSocketRequest
import akka.http.javadsl.model.ws.WebSocketUpgradeResponse
import akka.http.scaladsl.model.ws.TextMessage.{ Strict => ScalaStrictTextMessage }
import akka.http.scaladsl.model.ws.BinaryMessage.{ Strict => ScalaStrictBinaryMessage }
import akka.japi.Pair
import akka.javasdk.testkit.WebSocketRouteTester
import akka.javasdk.testkit.WebSocketRouteTester.WsConnection
import akka.stream.SystemMaterializer
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Keep
import akka.stream.testkit.TestPublisher
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.javadsl.TestSink
import akka.stream.testkit.javadsl.TestSource
import akka.util.ByteString

import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/**
 * INTERNAL API Used by the testkit
 */
@InternalApi
private[testkit] final class WebSocketRouteTesterImpl(runtimeHost: String, runtimePort: Int)(implicit
    system: ActorSystem[_])
    extends WebSocketRouteTester {
  private val materializer = SystemMaterializer(system).materializer

  override def wsTextConnection(path: String): WsConnection[String] =
    wsTextConnection(path, None)

  override def wsTextConnection(path: String, protocol: String): WsConnection[String] =
    wsTextConnection(path, Some(protocol))

  override def wsBinaryConnection(path: String): WsConnection[ByteString] =
    wsBinaryConnection(path, None)

  override def wsBinaryConnection(path: String, protocol: String): WsConnection[ByteString] =
    wsBinaryConnection(path, Some(protocol))

  private def wsTextConnection(path: String, protocol: Option[String]): WsConnection[String] = {
    val (absolutePath, wsFlow: Flow[Message, Message, CompletionStage[WebSocketUpgradeResponse]]) =
      clientFlowFor(path, protocol)

    val sink = TestSink.create[String](system)
    val source = TestSource.create[String](system)

    val matVal: Pair[
      Pair[TestPublisher.Probe[String], CompletionStage[WebSocketUpgradeResponse]],
      TestSubscriber.Probe[String]] = source
      .map[Message](TextMessage.create)
      .viaMat(wsFlow, Keep.both[TestPublisher.Probe[String], CompletionStage[WebSocketUpgradeResponse]])
      // FIXME: Akka HTTP missing Java API for TextMessage.Strict
      .mapAsync[ScalaStrictTextMessage](
        1,
        {
          case tm: TextMessage => tm.toStrict(3000, materializer)
          case _: BinaryMessage =>
            throw new IllegalArgumentException("Expected only TextMessage but got binary message")
        })
      .map(_.text)
      .toMat(
        sink,
        Keep.both[
          Pair[TestPublisher.Probe[String], CompletionStage[WebSocketUpgradeResponse]],
          TestSubscriber.Probe[String]])
      .run(materializer)

    val publisher = matVal.first.first
    val completion = matVal.first.second
    val subscriber = matVal.second

    val upgrade = waitForSuccessfullUpgrade(completion, absolutePath)

    new WsConnection(publisher, subscriber, upgrade.chosenSubprotocol);
  }

  private def wsBinaryConnection(path: String, protocol: Option[String]): WsConnection[ByteString] = {
    val (absolutePath, wsFlow: Flow[Message, Message, CompletionStage[WebSocketUpgradeResponse]]) =
      clientFlowFor(path, protocol)

    val sink = TestSink.create[ByteString](system)
    val source = TestSource.create[ByteString](system)

    val matVal: Pair[
      Pair[TestPublisher.Probe[ByteString], CompletionStage[WebSocketUpgradeResponse]],
      TestSubscriber.Probe[ByteString]] = source
      .map[Message](BinaryMessage.create)
      .viaMat(wsFlow, Keep.both[TestPublisher.Probe[ByteString], CompletionStage[WebSocketUpgradeResponse]])
      // FIXME: Akka HTTP missing Java API for BinaryMessage.Strict
      .mapAsync[ScalaStrictBinaryMessage](
        1,
        {
          case bm: BinaryMessage => bm.toStrict(3000, materializer)
          case _: TextMessage =>
            throw new IllegalArgumentException("Expected only BinaryMessage but got text message")
        })
      .map(_.data)
      .toMat(
        sink,
        Keep.both[
          Pair[TestPublisher.Probe[ByteString], CompletionStage[WebSocketUpgradeResponse]],
          TestSubscriber.Probe[ByteString]])
      .run(materializer)

    val publisher = matVal.first.first
    val completion = matVal.first.second
    val subscriber = matVal.second

    val upgrade = waitForSuccessfullUpgrade(completion, absolutePath)

    new WsConnection(publisher, subscriber, upgrade.chosenSubprotocol);
  }

  private def waitForSuccessfullUpgrade(
      cs: CompletionStage[WebSocketUpgradeResponse],
      absolutePath: String): WebSocketUpgradeResponse = {
    val upgradeResponse = cs.toCompletableFuture.get(3, TimeUnit.SECONDS)
    if (!upgradeResponse.isValid) {
      throw new RuntimeException(
        s"WebSocket connection to ws://$runtimeHost:$runtimePort$absolutePath was not successful: ${upgradeResponse.invalidationReason}")
    }
    upgradeResponse
  }

  private def clientFlowFor(path: String, protocol: Option[String]) = {
    val absolutePath = if (path.startsWith("/")) path else "/" + path
    val wsRequest = WebSocketRequest.create("ws://" + runtimeHost + ":" + runtimePort + absolutePath)
    val requestWithProtocol = protocol match {
      case Some(p) => wsRequest.requestSubprotocol(p)
      case None    => wsRequest
    }
    val wsFlow = Http
      .get(system)
      .webSocketClientFlow(requestWithProtocol)
    (absolutePath, wsFlow)
  }

}
