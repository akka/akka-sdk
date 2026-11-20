/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.control.NonFatal

import akka.actor.Scheduler
import akka.annotation.InternalApi
import akka.http.scaladsl.model.FormData
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.javasdk.a2a.A2aClientException
import akka.stream.Materializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

private object A2aOAuth2TokenCache {
  final case class Token(value: String, refreshAt: Instant)
}

/**
 * INTERNAL API
 *
 * Holds the access token of the OAuth 2.0 client credentials grant for one remote agent. A token is reused until
 * shortly before it expires, and concurrent callers share one token request.
 */
@InternalApi
private[akka] final class A2aOAuth2TokenCache(
    credentials: A2aAuth.OAuth2ClientCredentials,
    send: HttpRequest => Future[HttpResponse],
    mapper: ObjectMapper,
    timeout: FiniteDuration,
    scheduler: Scheduler,
    now: () => Instant = () => Instant.now())(implicit ec: ExecutionContext, materializer: Materializer) {
  import A2aOAuth2TokenCache.Token

  // Used when the token endpoint does not say how long the token lives
  private val DefaultLifetime = 5.minutes
  private val MaxRefreshMargin = 30.seconds
  private val MaxLifetime = 24.hours
  private val MaxResponseBytes = 64L * 1024

  private var cached: Option[Token] = None
  private var inFlight: Option[Future[Token]] = None

  def token(): Future[String] = synchronized {
    cached.filter(t => now().isBefore(t.refreshAt)) match {
      case Some(t) => Future.successful(t.value)
      case None =>
        val request = inFlight.getOrElse {
          // inFlight is set before the fetch starts and cleared before the promise completes, so it is cleared even
          // when the fetch completes on this thread, and a caller never sees a settled request still in flight
          val promise = Promise[Token]()
          inFlight = Some(promise.future)
          val fetched =
            try withTimeout(fetch())
            catch { case NonFatal(e) => Future.failed(e) }
          fetched.onComplete { result =>
            synchronized {
              inFlight = None
              result.foreach(t => cached = Some(t))
            }
            promise.complete(result)
          }
          promise.future
        }
        request.map(_.value)
    }
  }

  /**
   * Drops the token that the remote agent rejected. A token that has already been replaced stays.
   */
  def invalidate(rejected: String): Unit = synchronized {
    if (cached.exists(_.value == rejected)) cached = None
  }

  private def withTimeout(fetched: Future[Token]): Future[Token] = {
    val timedOut = Promise[Token]()
    val timer = scheduler.scheduleOnce(timeout)(
      timedOut.tryFailure(
        new A2aClientException(s"The token endpoint [${credentials.tokenUrl}] did not answer within $timeout")))
    fetched.onComplete(_ => timer.cancel())
    Future.firstCompletedOf(Seq(fetched, timedOut.future))
  }

  private def fetch(): Future[Token] = {
    val grant = Seq("grant_type" -> "client_credentials") ++ credentials.scope.map("scope" -> _)
    val request = credentials.authMethod match {
      case A2aAuth.ClientSecretBasic =>
        // RFC 6749 section 2.3.1 form-encodes the id and the secret before the Basic encoding
        def formEncode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
        HttpRequest(HttpMethods.POST, credentials.tokenUrl, entity = FormData(grant: _*).toEntity).withHeaders(
          Accept(MediaTypes.`application/json`),
          Authorization(BasicHttpCredentials(formEncode(credentials.clientId), formEncode(credentials.clientSecret))))
      case A2aAuth.ClientSecretPost =>
        val form = grant ++ Seq("client_id" -> credentials.clientId, "client_secret" -> credentials.clientSecret)
        HttpRequest(HttpMethods.POST, credentials.tokenUrl, entity = FormData(form: _*).toEntity)
          .withHeaders(Accept(MediaTypes.`application/json`))
    }
    send(request).flatMap { response =>
      response.entity.withSizeLimit(MaxResponseBytes).toStrict(10.seconds).map { entity =>
        val json: Option[JsonNode] =
          try Some(mapper.readTree(entity.data.utf8String))
          catch { case _: Exception => None }
        def field(name: String): Option[JsonNode] = json.flatMap(j => Option(j.get(name)))
        if (!response.status.isSuccess())
          throw new A2aClientException(
            s"The token endpoint [${credentials.tokenUrl}] answered ${response.status.intValue()}" +
            field("error").filter(_.isTextual).fold("")(e => s" (${e.asText()})"))
        field("access_token").filter(_.isTextual).map(_.asText()).filter(_.nonEmpty) match {
          case Some(value) =>
            // Some endpoints send the lifetime as a string. A lifetime beyond the cap would overflow the duration.
            val lifetime = field("expires_in")
              .flatMap(n => if (n.isNumber) Some(n.asLong()) else n.asText().trim.toLongOption)
              .map(seconds => math.min(seconds, MaxLifetime.toSeconds).seconds)
              .getOrElse(DefaultLifetime)
            val margin = MaxRefreshMargin.min(lifetime / 2)
            Token(value, now().plusMillis((lifetime - margin).toMillis))
          case None =>
            throw new A2aClientException(s"The token endpoint [${credentials.tokenUrl}] returned no access_token")
        }
      }
    }
  }
}
