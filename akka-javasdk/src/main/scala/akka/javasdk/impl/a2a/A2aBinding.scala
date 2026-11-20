/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import akka.annotation.InternalApi
import akka.javasdk.a2a.A2aAgentCard
import akka.javasdk.a2a.A2aClientException
import akka.javasdk.a2a.A2aRemoteErrorException
import com.fasterxml.jackson.databind.JsonNode

/**
 * INTERNAL API
 *
 * The protocol binding the client uses for a remote agent, chosen from the interfaces on its Agent Card.
 */
@InternalApi
private[akka] sealed trait A2aBinding {
  def url: String
  def tenant: Option[String]
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object A2aBinding {
  final case class JsonRpc(url: String, tenant: Option[String]) extends A2aBinding
  final case class HttpJson(url: String, tenant: Option[String]) extends A2aBinding

  private val JsonRpcName = "JSONRPC"
  private val HttpJsonName = "HTTP+JSON"

  /**
   * JSON-RPC when the card lists it, otherwise HTTP+JSON. An interface that names another protocol version than 1.x is
   * skipped.
   */
  def select(card: A2aAgentCard): A2aBinding = {
    val interfaces = card.supportedInterfaces().asScala.toSeq.filter { i =>
      i.url() != null && i.url().nonEmpty && (i.protocolVersion() == null || i.protocolVersion().startsWith("1"))
    }
    def tenant(i: A2aAgentCard.Interface) = Option(i.tenant()).filter(_.nonEmpty)
    def named(name: String) = interfaces.find(i => name.equalsIgnoreCase(i.protocolBinding()))
    named(JsonRpcName)
      .map(i => JsonRpc(i.url(), tenant(i)))
      .orElse(named(HttpJsonName).map(i => HttpJson(i.url(), tenant(i))))
      .getOrElse {
        val offered = card.supportedInterfaces().asScala.map(_.protocolBinding()).mkString("[", ", ", "]")
        throw new A2aClientException(
          s"The Agent Card of [${card.name()}] offers no supported binding, expected $JsonRpcName or $HttpJsonName, got $offered")
      }
  }

  /**
   * The error of a JSON-RPC response. The A2A error reason is taken from the code, or from an ErrorInfo in the data.
   */
  def jsonRpcError(error: JsonRpcError): A2aRemoteErrorException = {
    val reason = A2aErrorCode.fromCode(error.code).toScala.map(_.reason()).orElse(reasonIn(Option(error.data)))
    new A2aRemoteErrorException(error.code, reason.orNull, describe(error.message, reason))
  }

  /**
   * The error of an HTTP+JSON response, which is a Google RPC status wrapped in an `error` member.
   */
  def httpJsonError(status: Int, body: Option[JsonNode]): A2aClientException = {
    val error = body.flatMap(b => Option(b.get("error")))
    val reason = error.flatMap(e => reasonIn(Option(e.get("details"))))
    val message = error.flatMap(e => Option(e.get("message"))).filter(_.isTextual).map(_.asText())
    reason.flatMap(r => A2aErrorCode.values().find(_.reason() == r)) match {
      case Some(code) =>
        new A2aRemoteErrorException(code.code(), code.reason(), describe(message.getOrElse(code.message()), reason))
      case None =>
        new A2aClientException(s"The remote agent answered HTTP $status" + message.fold("")(m => s": $m"))
    }
  }

  private def describe(message: String, reason: Option[String]): String =
    reason.fold(message)(r => s"$message ($r)")

  // ErrorInfo is the entry of a details array, or the data itself, that has a reason in the A2A error domain
  private def reasonIn(details: Option[JsonNode]): Option[String] =
    details.flatMap { node =>
      val entries = if (node.isArray) node.elements().asScala.toSeq else Seq(node)
      entries
        .find(e =>
          e.hasNonNull("reason") && e.path("domain").asText(A2aErrorCode.ERROR_DOMAIN) == A2aErrorCode.ERROR_DOMAIN)
        .map(_.get("reason").asText())
    }
}
