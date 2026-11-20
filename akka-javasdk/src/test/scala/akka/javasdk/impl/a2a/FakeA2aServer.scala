/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.javasdk.JsonSupport
import akka.javasdk.a2a._
import akka.stream.scaladsl.Source
import com.fasterxml.jackson.databind.JsonNode

object FakeA2aServer {
  final case class Seen(method: String, path: String, headers: Map[String, String])
}

/**
 * A small A2A server for client tests. It answers the Agent Card, and SendMessage, SendStreamingMessage, GetTask and
 * CancelTask over both bindings. Every request is recorded.
 */
final class FakeA2aServer(implicit system: ActorSystem[_]) {
  import FakeA2aServer.Seen
  import system.executionContext

  private val mapper = JsonSupport.getObjectMapper

  val seen = new ConcurrentLinkedQueue[Seen]()
  val cardRequests = new AtomicInteger()

  /** Token requests. The token endpoint is at /token and issues tok-1, tok-2 and so on. */
  val tokenRequests = new AtomicInteger()

  /** The bindings the card lists. */
  @volatile var bindings: Seq[String] = Seq("JSONRPC", "HTTP+JSON")

  /** The skills the card lists. */
  @volatile var skills: Seq[A2aAgentSkill] = Seq.empty

  /**
   * The reply to SendMessage on the JSON-RPC binding, given the request's params: a JSON-RPC error or a result. By
   * default a completed task that carries the skill id in its metadata.
   */
  @volatile var sendReply: Option[JsonNode => Either[JsonRpcError, A2aSendMessageResponse]] = None

  /** Requests that fail this check get a 401. */
  @volatile var authorized: HttpRequest => Boolean = _ => true

  @volatile private var baseUrl = ""

  def cardUrl: String = s"$baseUrl/.well-known/agent-card.json"

  def tokenUrl: String = s"$baseUrl/token"

  def requests: Seq[Seen] = seen.asScala.toSeq

  def start(tls: Option[SSLContext] = None, requireClientCertificate: Boolean = false): Future[Http.ServerBinding] = {
    val builder = Http().newServerAt("127.0.0.1", 0)
    val bound = tls match {
      case Some(context) =>
        val https = ConnectionContext.httpsServer { () =>
          val engine = context.createSSLEngine()
          engine.setUseClientMode(false)
          engine.setNeedClientAuth(requireClientCertificate)
          engine
        }
        builder.enableHttps(https).bind(route)
      case None => builder.bind(route)
    }
    bound.map { binding =>
      baseUrl = s"${if (tls.isDefined) "https" else "http"}://localhost:${binding.localAddress.getPort}"
      binding
    }
  }

  private def task(id: String, contextId: String, state: A2aTaskState, skill: Option[String] = None): A2aTask =
    new A2aTask(
      id,
      contextId,
      new A2aTaskStatus(state, null, null),
      null,
      null,
      skill.map(s => java.util.Map.of[String, Object]("skillId", s)).orNull)

  private def card: A2aAgentCard =
    new A2aAgentCard(
      "fake",
      "A fake agent",
      bindings.map { b =>
        new A2aAgentCard.Interface(if (b == "JSONRPC") s"$baseUrl/a2a" else s"$baseUrl/rest", b, null, "1.0")
      }.asJava,
      null,
      "1.0",
      null,
      new A2aAgentCard.Capabilities(true, false, null, false),
      null,
      null,
      null,
      null,
      skills.asJava,
      null,
      null)

  private def jsonEntity(value: Any): HttpEntity.Strict =
    HttpEntity(ContentTypes.`application/json`, mapper.writeValueAsBytes(value))

  private def contextOf(params: JsonNode): String =
    Option(params.path("message").get("contextId")).map(_.asText()).getOrElse("new-context")

  private def skillOf(params: JsonNode): Option[String] =
    Option(params.path("metadata").get("skillId")).map(_.asText())

  private def streamResponses(params: JsonNode): Seq[A2aStreamResponse] = {
    val context = contextOf(params)
    Seq(
      A2aStreamResponse.of(
        new A2aTaskStatusUpdate("task-1", context, new A2aTaskStatus(A2aTaskState.WORKING, null, null), null)),
      A2aStreamResponse.of(task("task-1", context, A2aTaskState.COMPLETED)))
  }

  private def notFound: JsonRpcError = JsonRpcError.of(A2aErrorCode.TASK_NOT_FOUND)

  private val notFoundBody =
    """{"error":{"code":404,"status":"NOT_FOUND","message":"Task not found","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"TASK_NOT_FOUND","domain":"a2a-protocol.org"}]}}"""

  private def jsonRpc: Route =
    entity(as[String]) { body =>
      val request = mapper.readTree(body)
      val id = request.get("id")
      val params = request.get("params")
      def result(value: Any) = complete(jsonEntity(JsonRpcResponse.success(id, mapper.valueToTree(value))))
      request.get("method").asText() match {
        case A2aMethods.SEND_MESSAGE =>
          sendReply.fold[Either[JsonRpcError, A2aSendMessageResponse]](
            Right(
              A2aSendMessageResponse.of(task("task-1", contextOf(params), A2aTaskState.COMPLETED, skillOf(params)))))(
            _(params)) match {
            case Right(reply) => result(reply)
            case Left(error)  => complete(jsonEntity(JsonRpcResponse.failure(id, error)))
          }
        case A2aMethods.SEND_STREAMING_MESSAGE =>
          complete(Source(streamResponses(params)).map { r =>
            ServerSentEvent(mapper.writeValueAsString(JsonRpcResponse.success(id, mapper.valueToTree(r))))
          })
        case A2aMethods.GET_TASK =>
          val taskId = params.get("id").asText()
          if (taskId == "missing") complete(jsonEntity(JsonRpcResponse.failure(id, notFound)))
          else result(task(taskId, "ctx", A2aTaskState.WORKING))
        case A2aMethods.CANCEL_TASK =>
          result(task(params.get("id").asText(), "ctx", A2aTaskState.CANCELED))
        case _ =>
          complete(jsonEntity(JsonRpcResponse.failure(id, JsonRpcError.of(A2aErrorCode.METHOD_NOT_FOUND))))
      }
    }

  private def httpJson: Route =
    pathPrefix("rest") {
      path("message:send") {
        post {
          entity(as[String]) { body =>
            val params = mapper.readTree(body)
            complete(jsonEntity(
              A2aSendMessageResponse.of(task("task-1", contextOf(params), A2aTaskState.COMPLETED, skillOf(params)))))
          }
        }
      } ~ path("message:stream") {
        post {
          entity(as[String]) { body =>
            complete(
              Source(streamResponses(mapper.readTree(body))).map(r => ServerSentEvent(mapper.writeValueAsString(r))))
          }
        }
      } ~ path("tasks" / Segment) { segment =>
        if (segment.endsWith(":cancel"))
          post(complete(jsonEntity(task(segment.stripSuffix(":cancel"), "ctx", A2aTaskState.CANCELED))))
        else
          get {
            if (segment == "missing")
              complete(
                HttpResponse(StatusCodes.NotFound, entity = HttpEntity(ContentTypes.`application/json`, notFoundBody)))
            else complete(jsonEntity(task(segment, "ctx", A2aTaskState.WORKING)))
          }
      }
    }

  private def route: Route =
    extractRequest { request =>
      seen.add(
        Seen(
          request.method.value,
          request.uri.path.toString,
          request.headers.map(h => h.lowercaseName() -> h.value()).toMap))
      if (request.uri.path.toString != "/token" && !authorized(request)) complete(StatusCodes.Unauthorized)
      else
        path("token") {
          post {
            val n = tokenRequests.incrementAndGet()
            complete(
              HttpEntity(
                ContentTypes.`application/json`,
                s"""{"access_token":"tok-$n","token_type":"Bearer","expires_in":3600}"""))
          }
        } ~ path(".well-known" / "agent-card.json") {
          get {
            cardRequests.incrementAndGet()
            complete(jsonEntity(card))
          }
        } ~ path("a2a")(post(jsonRpc)) ~ httpJson
    }
}
