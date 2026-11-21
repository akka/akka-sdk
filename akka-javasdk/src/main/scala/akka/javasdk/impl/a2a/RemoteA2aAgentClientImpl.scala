/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.net.URLEncoder
import java.security.PublicKey
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.jdk.FutureConverters._
import scala.util.control.NonFatal

import akka.NotUsed
import akka.actor.Scheduler
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.sse.EventStreamParser
import akka.javasdk.JsonSupport
import akka.javasdk.a2a._
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import akka.stream.javadsl.{ Source => JavaSource }
import akka.stream.scaladsl.Source
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 *
 * What the calls to one remote agent share: the TLS context, the access token and the Agent Card. The client for an
 * agent is created per calling component, but the state is created once.
 */
@InternalApi
private[akka] final class RemoteA2aAgentState(
    val system: ActorSystem[_],
    val settings: A2aRemoteAgentSettings,
    val route: Option[A2aServiceRoute] = None) {
  import RemoteA2aAgentState._

  implicit val ec: ExecutionContext = system.executionContext
  implicit val materializer: Materializer = SystemMaterializer(system).materializer

  val http: HttpExt = Http()(system.classicSystem)

  // Built when the state is created, so that a bad certificate fails when the agent is first used
  val connectionContext: Option[HttpsConnectionContext] =
    httpsContext(settings.caBundle, settings.clientCertificate, settings.clientKey, "")

  def send(request: HttpRequest): Future[HttpResponse] = sendWith(connectionContext)(request)

  // Headers that only a call to another Akka service carries
  def serviceHeaders: Seq[HttpHeader] = route.fold(Seq.empty[HttpHeader])(_.headers)

  // The token endpoint is usually another host than the agent, so it has its own TLS settings
  val tokens: Option[A2aOAuth2TokenCache] = settings.auth match {
    case oauth: A2aAuth.OAuth2ClientCredentials =>
      val tokenContext =
        httpsContext(oauth.tokenCaBundle, oauth.tokenClientCertificate, oauth.tokenClientKey, "auth.token-")
      Some(
        new A2aOAuth2TokenCache(
          oauth,
          sendWith(tokenContext),
          mapper,
          settings.timeout,
          system.classicSystem.scheduler))
    case _ => None
  }

  private def sendWith(context: Option[HttpsConnectionContext])(request: HttpRequest): Future[HttpResponse] =
    route.flatMap(_.poolSettings) match {
      case Some(pool) =>
        http.singleRequest(request, context.getOrElse(http.defaultClientHttpsContext), pool)
      case None =>
        context.fold(http.singleRequest(request))(ctx => http.singleRequest(request, ctx))
    }

  private def httpsContext(
      caBundle: Option[String],
      clientCertificate: Option[String],
      clientKey: Option[String],
      keyPrefix: String): Option[HttpsConnectionContext] =
    if (caBundle.isEmpty && clientCertificate.isEmpty) None
    else
      try Some(ConnectionContext.httpsClient(A2aTls.sslContext(caBundle, clientCertificate, clientKey)))
      catch {
        // The cause is left out, so that the PEM text of a key is not printed
        case e: IllegalArgumentException =>
          val names = Seq("ca-bundle", "client-certificate", "client-key")
            .map(key => s"akka.javasdk.a2a.remote-agents.${settings.name}.$keyPrefix$key")
            .mkString(", ")
          throw new IllegalArgumentException(s"${e.getMessage}. Check the settings $names")
      }

  // Parsed when the state is created, so that a bad key fails when the agent is first used
  val cardSigningKey: Option[PublicKey] =
    settings.cardVerification.flatMap(_.publicKey).map { pem =>
      try AgentCardSigning.parsePublicKey(pem)
      catch {
        case e: IllegalArgumentException =>
          throw new IllegalArgumentException(
            s"akka.javasdk.a2a.remote-agents.${settings.name}.card-signing-key is invalid: ${e.getMessage}")
      }
    }

  private val card = new AtomicReference[Option[CachedCard]](None)

  def cachedCard(): Option[A2aAgentCard] =
    card.get().filter(c => System.nanoTime() - c.fetchedAtNanos < settings.cardRefresh.toNanos).map(_.card)

  def cacheCard(fetched: A2aAgentCard): Unit = card.set(Some(CachedCard(fetched, System.nanoTime())))
}

private object RemoteA2aAgentState {
  final case class CachedCard(card: A2aAgentCard, fetchedAtNanos: Long)

  // Agents in the field send members that this version does not know
  val mapper: ObjectMapper =
    JsonSupport.getObjectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object RemoteA2aAgentClientImpl {

  /**
   * The response, or the timeout error when the response takes longer. A response that arrives after the timeout is
   * discarded, so that its connection returns to the pool.
   */
  def within(response: Future[HttpResponse], timeout: FiniteDuration, scheduler: Scheduler, error: => Throwable)(
      implicit
      ec: ExecutionContext,
      materializer: Materializer): Future[HttpResponse] = {
    val result = Promise[HttpResponse]()
    val timer = scheduler.scheduleOnce(timeout)(result.tryFailure(error))
    response.onComplete { r =>
      timer.cancel()
      if (!result.tryComplete(r)) r.foreach(_.discardEntityBytes())
    }
    result.future
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class RemoteA2aAgentClientImpl(
    state: RemoteA2aAgentState,
    extraHeaders: Seq[HttpHeader],
    selectBinding: A2aAgentCard => A2aBinding = A2aBinding.select,
    timeoutOverride: Option[FiniteDuration] = None)
    extends RemoteA2aAgentClient {

  import RemoteA2aAgentState.mapper
  import state.ec
  import state.materializer

  private val log = LoggerFactory.getLogger(classOf[RemoteA2aAgentClient])
  private val settings = state.settings
  private val timeout = timeoutOverride.getOrElse(settings.timeout)
  private val requestIds = new AtomicLong()
  private val MaxResponseBytes = 16L * 1024 * 1024
  private val A2aVersion = "1.0"

  // Calls that return an Agent Card, a task or a message

  private final class RequestImpl[R](send: () => Future[R]) extends RemoteA2aAgentClient.Request[R] {
    override def invokeAsync(): CompletionStage[R] = send().asJava
    override def invoke(): R = await(invokeAsync())
  }

  override def card(): RemoteA2aAgentClient.Request[A2aAgentCard] = new RequestImpl(() => cardFuture())

  private def cardFuture(): Future[A2aAgentCard] =
    state.cachedCard() match {
      case Some(card) => Future.successful(card)
      case None =>
        call(() => request(HttpMethods.GET, settings.cardUrl)).flatMap { response =>
          strict(response).flatMap { body =>
            if (!response.status.isSuccess())
              throw new A2aClientException(
                s"The Agent Card of remote agent [${settings.name}] could not be fetched, ${settings.cardUrl} answered ${response.status
                  .intValue()}")
            def invalid(e: Throwable) =
              new A2aClientException(
                s"The Agent Card of remote agent [${settings.name}] at ${settings.cardUrl} is not valid: ${e.getMessage}",
                e)
            val tree =
              try mapper.readTree(body)
              catch { case NonFatal(e) => throw invalid(e) }
            verifySignature(tree).map { _ =>
              val card =
                try mapper.treeToValue(tree, classOf[A2aAgentCard])
                catch { case NonFatal(e) => throw invalid(e) }
              state.cacheCard(card)
              card
            }
          }
        }
    }

  // A card that must be signed and is not signed by the trusted key is refused, so no call reaches the agent
  private def verifySignature(card: JsonNode): Future[Unit] =
    settings.cardVerification match {
      case None => Future.unit
      case Some(verification) =>
        keyResolver(verification).map { resolver =>
          val result = AgentCardSigning.verify(card, resolver)
          if (!result.verified())
            throw new A2aClientException(
              s"The Agent Card of remote agent [${settings.name}] is not signed by a trusted key: ${result.failure()}")
        }
    }

  private def keyResolver(verification: A2aCardVerification): Future[AgentCardSigning.KeyResolver] =
    state.cardSigningKey match {
      case Some(key) => Future.successful(AgentCardSigning.KeyResolver.forAnyKeyId(key))
      case None =>
        val jwksUrl = verification.jwksUrl.get
        // The key set is fetched without the agent's credentials, which belong to the agent alone
        RemoteA2aAgentClientImpl
          .within(
            state.send(HttpRequest(HttpMethods.GET, jwksUrl).withHeaders(Accept(MediaTypes.`application/json`))),
            timeout,
            state.system.classicSystem.scheduler,
            new A2aClientException(s"The key set of remote agent [${settings.name}] was not fetched within $timeout"))
          .flatMap { response =>
            strict(response).map { body =>
              if (!response.status.isSuccess())
                throw new A2aClientException(
                  s"The key set of remote agent [${settings.name}] could not be fetched, $jwksUrl answered ${response.status
                    .intValue()}")
              AgentCardSigning.KeyResolver.forJwks(new String(body, StandardCharsets.UTF_8))
            }
          }
    }

  override def send(message: A2aMessage): RemoteA2aAgentClient.SendRequest =
    new SendRequestImpl(None, message, None)

  override def send(skill: String, message: A2aMessage): RemoteA2aAgentClient.SendRequest =
    new SendRequestImpl(Some(requireSkill(skill)), message, None)

  private def requireSkill(skill: String): String = {
    if (skill == null || skill.isBlank) throw new IllegalArgumentException("The skill id must not be blank")
    skill
  }

  private final class SendRequestImpl(skill: Option[String], message: A2aMessage, contextId: Option[String])
      extends RemoteA2aAgentClient.SendRequest {

    override def inContext(contextId: String): RemoteA2aAgentClient.SendRequest =
      new SendRequestImpl(skill, message, Some(contextId))

    override def invokeAsync(): CompletionStage[A2aSendMessageResponse] =
      cardFuture().flatMap { card =>
        val body = sendMessageRequest(skill, message, contextId)
        selectBinding(card) match {
          case b: A2aBinding.JsonRpc =>
            jsonRpc(b, A2aMethods.SEND_MESSAGE, body).map(mapper.treeToValue(_, classOf[A2aSendMessageResponse]))
          case b: A2aBinding.HttpJson =>
            httpJson(HttpMethods.POST, path(b, "message:send"), Some(body)).map(
              mapper.treeToValue(_, classOf[A2aSendMessageResponse]))
        }
      }.asJava

    override def invoke(): A2aSendMessageResponse = await(invokeAsync())
  }

  override def stream(message: A2aMessage): JavaSource[A2aStreamResponse, NotUsed] =
    streamFor(None, message)

  override def stream(skill: String, message: A2aMessage): JavaSource[A2aStreamResponse, NotUsed] =
    streamFor(Some(requireSkill(skill)), message)

  private def streamFor(skill: Option[String], message: A2aMessage): JavaSource[A2aStreamResponse, NotUsed] =
    Source
      .futureSource(cardFuture().map { card =>
        val body = sendMessageRequest(skill, message, None)
        selectBinding(card) match {
          case b: A2aBinding.JsonRpc =>
            events(
              () => jsonRpcRequest(b, A2aMethods.SEND_STREAMING_MESSAGE, body, MediaTypes.`text/event-stream`),
              json => {
                val rpc = mapper.treeToValue(json, classOf[JsonRpcResponse])
                if (rpc.error != null) throw A2aBinding.jsonRpcError(rpc.error)
                else if (rpc.result == null) throw new A2aClientException("A streamed reply has no result")
                else mapper.treeToValue(rpc.result, classOf[A2aStreamResponse])
              },
              JsonRpcErrors)
          case b: A2aBinding.HttpJson =>
            events(
              () =>
                request(HttpMethods.POST, path(b, "message:stream"), MediaTypes.`text/event-stream`)
                  .withEntity(json(body)),
              mapper.treeToValue(_, classOf[A2aStreamResponse]),
              HttpJsonErrors)
        }
      })
      .mapMaterializedValue(_ => NotUsed: NotUsed)
      .asJava

  override def getTask(taskId: String): RemoteA2aAgentClient.Request[A2aTask] =
    new RequestImpl(() => getTaskFuture(taskId))

  private def getTaskFuture(taskId: String): Future[A2aTask] =
    cardFuture().flatMap { card =>
      selectBinding(card) match {
        case b: A2aBinding.JsonRpc =>
          jsonRpc(b, A2aMethods.GET_TASK, mapper.valueToTree(new A2aGetTaskRequest(b.tenant.orNull, taskId, null)))
            .map(mapper.treeToValue(_, classOf[A2aTask]))
        case b: A2aBinding.HttpJson =>
          httpJson(HttpMethods.GET, path(b, s"tasks/${encode(taskId)}"), None)
            .map(mapper.treeToValue(_, classOf[A2aTask]))
      }
    }

  override def cancel(taskId: String): RemoteA2aAgentClient.Request[A2aTask] =
    new RequestImpl(() => cancelFuture(taskId))

  private def cancelFuture(taskId: String): Future[A2aTask] =
    cardFuture().flatMap { card =>
      selectBinding(card) match {
        case b: A2aBinding.JsonRpc =>
          jsonRpc(
            b,
            A2aMethods.CANCEL_TASK,
            mapper.valueToTree(new A2aCancelTaskRequest(b.tenant.orNull, taskId, null)))
            .map(mapper.treeToValue(_, classOf[A2aTask]))
        case b: A2aBinding.HttpJson =>
          httpJson(HttpMethods.POST, path(b, s"tasks/${encode(taskId)}:cancel"), Some(mapper.createObjectNode()))
            .map(mapper.treeToValue(_, classOf[A2aTask]))
      }
    }

  // Requests

  private def sendMessageRequest(skill: Option[String], message: A2aMessage, contextId: Option[String]): ObjectNode = {
    val withDefaults = new A2aMessage(
      Option(message.messageId()).getOrElse(UUID.randomUUID().toString),
      contextId.getOrElse(message.contextId()),
      message.taskId(),
      Option(message.role()).getOrElse(A2aRole.USER),
      message.parts(),
      message.metadata(),
      message.extensions(),
      message.referenceTaskIds())
    val metadata = skill.map(s => java.util.Map.of[String, Object]("skillId", s)).orNull
    mapper.valueToTree(new A2aSendMessageRequest(null, withDefaults, null, metadata))
  }

  private def path(binding: A2aBinding, suffix: String): String = {
    val base = binding.url.stripSuffix("/")
    binding.tenant.fold(s"$base/$suffix")(t => s"$base/${encode(t)}/$suffix")
  }

  private def encode(segment: String): String =
    URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")

  private def request(method: HttpMethod, url: String, accept: MediaType = MediaTypes.`application/json`): HttpRequest =
    HttpRequest(method, url).withHeaders(
      Accept(accept) +: RawHeader("A2A-Version", A2aVersion) +: extraHeaders.toVector)

  private def json(body: JsonNode): HttpEntity.Strict =
    HttpEntity(ContentTypes.`application/json`, mapper.writeValueAsBytes(body))

  private def jsonRpcRequest(
      binding: A2aBinding,
      method: String,
      params: JsonNode,
      accept: MediaType = MediaTypes.`application/json`): HttpRequest =
    request(HttpMethods.POST, binding.url, accept).withEntity(
      json(
        mapper.valueToTree(
          JsonRpcRequest.of(mapper.getNodeFactory.numberNode(requestIds.incrementAndGet()), method, params))))

  private def jsonRpc(binding: A2aBinding, method: String, params: JsonNode): Future[JsonNode] =
    call(() => jsonRpcRequest(binding, method, params)).flatMap { response =>
      strict(response).map { body =>
        val rpc =
          try Some(mapper.readValue(body, classOf[JsonRpcResponse]))
          catch { case NonFatal(_) => None }
        rpc match {
          case Some(r) if r.error != null  => throw A2aBinding.jsonRpcError(r.error)
          case Some(r) if r.result != null => r.result
          case _ =>
            throw new A2aClientException(
              s"Remote agent [${settings.name}] answered HTTP ${response.status.intValue()} without a JSON-RPC result")
        }
      }
    }

  private def httpJson(method: HttpMethod, url: String, body: Option[JsonNode]): Future[JsonNode] =
    call(() => body.fold(request(method, url))(b => request(method, url).withEntity(json(b)))).flatMap { response =>
      strict(response).map { bytes =>
        val parsed = readTree(bytes)
        if (!response.status.isSuccess()) throw A2aBinding.httpJsonError(response.status.intValue(), parsed)
        parsed.getOrElse(
          throw new A2aClientException(s"Remote agent [${settings.name}] returned a reply that is not JSON"))
      }
    }

  private def readTree(bytes: Array[Byte]): Option[JsonNode] =
    try Option(mapper.readTree(bytes)).filterNot(_.isMissingNode)
    catch { case NonFatal(_) => None }

  private def strict(response: HttpResponse): Future[Array[Byte]] =
    response.entity.withSizeLimit(MaxResponseBytes).toStrict(timeout).map(_.data.toArray)

  private sealed trait StreamErrors
  private case object JsonRpcErrors extends StreamErrors
  private case object HttpJsonErrors extends StreamErrors

  private def events[T](
      makeRequest: () => HttpRequest,
      parse: JsonNode => T,
      errors: StreamErrors): Source[T, NotUsed] =
    Source
      .futureSource(call(makeRequest).map { response =>
        if (response.status.isSuccess())
          response.entity.dataBytes
            .via(EventStreamParser(1024 * 1024, 4 * 1024 * 1024))
            .filter(_.data.nonEmpty)
            .map(event =>
              parse(readTree(event.data.getBytes(StandardCharsets.UTF_8))
                .getOrElse(throw new A2aClientException("A streamed event is not valid JSON"))))
        else
          Source.futureSource(strict(response).map { bytes =>
            val failure = errors match {
              case JsonRpcErrors =>
                readTree(bytes)
                  .flatMap(n => Option(mapper.treeToValue(n, classOf[JsonRpcResponse])).flatMap(r => Option(r.error)))
                  .map(A2aBinding.jsonRpcError(_): A2aClientException)
                  .getOrElse(new A2aClientException(
                    s"Remote agent [${settings.name}] answered HTTP ${response.status.intValue()}"))
              case HttpJsonErrors => A2aBinding.httpJsonError(response.status.intValue(), readTree(bytes))
            }
            Source.failed[T](failure)
          })
      })
      .mapMaterializedValue(_ => NotUsed)

  // Sends a request with the credentials of the remote agent. The request is built again for each attempt, because a
  // rejected OAuth token is replaced once. Only the time to the response headers is limited, so a stream can run
  // longer than the timeout.
  private def call(makeRequest: () => HttpRequest): Future[HttpResponse] = {
    def attempt(retryOnUnauthorized: Boolean): Future[HttpResponse] = {
      val request = makeRequest()
      // The Agent Card names the URLs of the calls, so a card can point them at plain http
      if (settings.auth != A2aAuth.NoAuth && !A2aRemoteAgentSettings.safeForCredentials(request.uri.toString))
        Future.failed(
          new A2aClientException(
            s"Remote agent [${settings.name}] was not called at ${request.uri}, because the client sends credentials " +
            "only to https:// URLs, or to http:// URLs on localhost"))
      else
        authorization().flatMap { case (headers, usedToken) =>
          state.send(addHeaders(request, headers)).flatMap { response =>
            if (response.status == StatusCodes.Unauthorized && retryOnUnauthorized && usedToken.isDefined) {
              response.discardEntityBytes()
              usedToken.foreach(t => state.tokens.foreach(_.invalidate(t)))
              log.debug("Remote A2A agent [{}] rejected the access token, requesting a new one", settings.name)
              attempt(retryOnUnauthorized = false)
            } else Future.successful(response)
          }
        }
    }
    RemoteA2aAgentClientImpl
      .within(
        attempt(retryOnUnauthorized = true),
        timeout,
        state.system.classicSystem.scheduler,
        new A2aClientException(s"Remote agent [${settings.name}] did not answer within $timeout"))
      .recoverWith {
        case e: A2aClientException => Future.failed(e)
        case NonFatal(e) =>
          Future.failed(new A2aClientException(s"Calling remote agent [${settings.name}] failed: ${e.getMessage}", e))
      }
  }

  private def addHeaders(request: HttpRequest, headers: Seq[HttpHeader]): HttpRequest =
    request.withHeaders(request.headers ++ headers)

  private def authorization(): Future[(Seq[HttpHeader], Option[String])] =
    settings.auth match {
      case A2aAuth.NoAuth => Future.successful((Nil, None))
      case A2aAuth.Header(name, value) =>
        Future.successful((Seq(RawHeader(name, value)), None))
      case _: A2aAuth.OAuth2ClientCredentials =>
        state.tokens.get.token().map(t => (Seq(Authorization(OAuth2BearerToken(t))), Some(t)))
    }

  private def await[T](stage: CompletionStage[T]): T =
    try stage.toCompletableFuture.get(timeout.toMillis + 5000, TimeUnit.MILLISECONDS)
    catch {
      case e: ExecutionException if e.getCause != null => throw e.getCause
      case _: TimeoutException =>
        throw new A2aClientException(s"Remote agent [${settings.name}] did not answer within $timeout")
    }
}
