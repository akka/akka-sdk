/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import scala.jdk.FutureConverters.CompletionStageOps

import akka.NotUsed
import akka.annotation.InternalApi
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.ws.BinaryMessage
import akka.http.javadsl.model.ws.Message
import akka.http.javadsl.model.ws.TextMessage
import akka.http.javadsl.model.ws.WebSocketUpgrade
import akka.stream.Materializer
import akka.stream.javadsl.{ Flow => JavaDslFlow }
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object WebSockets {

  private val log = LoggerFactory.getLogger(classOf[WebSockets.type])

  def textWebSocketResponse(
      request: HttpRequest,
      protocol: Option[String],
      stringFlow: JavaDslFlow[String, String, NotUsed])(implicit mat: Materializer): HttpResponse = {
    val wsUpgrade = getUpgrade(request)
    val adaptedFlow = adaptStringFlow(stringFlow.asScala)
    protocol match {
      case None              => wsUpgrade.handleMessagesWith(adaptedFlow)
      case Some(subprotocol) => wsUpgrade.handleMessagesWith(adaptedFlow, subprotocol)
    }
  }

  def binaryWebSocketResponse(
      request: HttpRequest,
      protocol: Option[String],
      stringFlow: JavaDslFlow[ByteString, ByteString, NotUsed])(implicit mat: Materializer): HttpResponse = {
    val wsUpgrade = getUpgrade(request)
    val adaptedFlow =
      adaptBytesFlow(stringFlow.asScala)
    protocol match {
      case Some(subprotocol) => wsUpgrade.handleMessagesWith(adaptedFlow, subprotocol)
      case None              => wsUpgrade.handleMessagesWith(adaptedFlow)
    }
  }

  private def getUpgrade(request: HttpRequest): WebSocketUpgrade =
    request
      .getAttribute(akka.http.javadsl.model.AttributeKeys.webSocketUpgrade)
      .orElseThrow(() => new IllegalArgumentException("Request expected to be a websocket upgrade"))

  private def adaptStringFlow(userFlow: Flow[String, String, NotUsed])(implicit
      mat: Materializer): JavaDslFlow[Message, Message, NotUsed] =
    Flow[Message]
      .mapAsync(1) {
        case tm: TextMessage =>
          tm.toStrict(3000, mat).asScala // FIXME timeout from config
        case _: BinaryMessage =>
          val msg = "Got binary websocket message but websocket only supports text messages"
          log.warn(msg)
          throw new IllegalArgumentException(msg)
      }
      .map(_.text)
      .via(userFlow)
      .map[Message]((text: String) => TextMessage.create(text))
      .asJava

  private def adaptBytesFlow(userFlow: Flow[ByteString, ByteString, NotUsed])(implicit
      mat: Materializer): JavaDslFlow[Message, Message, NotUsed] =
    Flow[Message]
      .mapAsync(1) {
        case bm: BinaryMessage => bm.toStrict(3000, mat).asScala // FIXME timeout from config
        case _: TextMessage =>
          val msg = "Got binary websocket message but websocket only supports text messages"
          log.warn(msg)
          throw new IllegalArgumentException(msg)
      }
      .map(_.data)
      .via(userFlow)
      .map[Message]((data: ByteString) => BinaryMessage.create(data))
      .asJava

}
