/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.backoffice

import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.JavaDurationOps
import scala.util.Failure
import scala.util.Success

import akka.actor.typed._
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.annotation.InternalApi
import akka.grpc.GrpcClientSettings
import akka.pattern.StatusReply
import akka.util.Timeout
import kalix.api.auth.v1alpha.auth.AuthClient
import kalix.api.auth.v1alpha.auth.CreateAccessTokenRequest
import org.slf4j.LoggerFactory

/**
 * This is primarily used in dev mode, when interacting with other services via the backoffice proxy.
 */
@InternalApi
private[impl] object BackofficeAccessTokenCache extends ExtensionId[BackofficeAccessTokenCache] {

  override def createExtension(system: ActorSystem[_]): BackofficeAccessTokenCache = {
    val requestTimeout = system.settings.config.getDuration("akka.javasdk.dev-mode.backoffice.request-timeout").toScala
    new BackofficeAccessTokenCache(requestTimeout)(system)
  }

  def get(system: ActorSystem[_]): BackofficeAccessTokenCache = apply(system)

  private sealed trait Protocol
  private case class Init(apiServerHost: String, apiServerPort: Int, refreshToken: String) extends Protocol
  private case class GetAccessToken(replyTo: ActorRef[StatusReply[String]]) extends Protocol
  private case class AccessToken(token: String, expiry: Instant) extends Protocol
  private case class GetFailed(exception: Throwable) extends Protocol
}

@InternalApi
private[impl] class BackofficeAccessTokenCache private (requestTimeout: FiniteDuration)(implicit sys: ActorSystem[_])
    extends Extension {

  import BackofficeAccessTokenCache._

  private val log = LoggerFactory.getLogger(getClass)

  private val actor = sys.systemActorOf(awaitingInit, "sdk-backoffice-access-token-cache")
  // Slightly longer than request timeout so that the request timeout failure should be propagated before the ask
  // times out
  private implicit val askTimeout: Timeout = Timeout(requestTimeout.plus(100.millis))
  private implicit val scheduler: Scheduler = sys.scheduler

  def init(apiServerHost: String, apiServerPort: Int, refreshToken: String): Unit = {
    actor ! Init(apiServerHost, apiServerPort, refreshToken)
  }

  def accessToken(): Future[String] = {
    actor.askWithStatus[String](reply => GetAccessToken(reply))
  }

  private def awaitingInit: Behavior[Protocol] = Behaviors.receiveMessage {
    case Init(apiServerHost, apiServerPort, refreshToken) =>
      // We log the first 4 characters of the refresh token, which is not sensitive, it should be "kxr_", this will
      // verify that it is a refresh token.
      log.debug(
        s"BackofficeAccessTokenCache initialized with refresh token starting with {}... using server {}:{}",
        refreshToken.take(4),
        apiServerHost,
        apiServerPort)
      val authClient = AuthClient(GrpcClientSettings.connectToServiceAt(apiServerHost, apiServerPort))
      new Initialized(authClient, refreshToken).idle

    case GetAccessToken(replyTo) =>
      replyTo ! StatusReply.error("Cannot use BackofficeAccessTokenCache until it has been initialized")
      Behaviors.same

    case other =>
      log.debug("Unexpected message while awaiting init: {}", other)
      Behaviors.same
  }

  private class Initialized(authClient: AuthClient, refreshToken: String) {
    def idle: Behavior[Protocol] = Behaviors.receive {
      case (ctx, GetAccessToken(replyTo)) =>
        log.debug("Fetching access token")
        fetchAccessToken(ctx)
        loading(Seq(replyTo))
      case (_, msg) =>
        log.debug("Unexpected message while idle: {}", msg)
        Behaviors.same
    }

    private def loading(waiting: Seq[ActorRef[StatusReply[String]]]): Behavior[Protocol] = Behaviors.receiveMessage {
      case GetAccessToken(replyTo) =>
        loading(waiting :+ replyTo)

      case AccessToken(token, expiry) =>
        log.debug("Access token fetched with expiry at {}", expiry)
        waiting.foreach { replyTo =>
          replyTo ! StatusReply.success(token)
        }
        caching(token, expiry)

      case GetFailed(throwable) =>
        log.debug("Access token failed to load", throwable)
        waiting.foreach { replyTo =>
          replyTo ! StatusReply.error(throwable)
        }
        idle

      case msg =>
        log.debug("Unexpected message while loading: {}", msg)
        Behaviors.same
    }

    private def caching(token: String, expiry: Instant): Behavior[Protocol] = Behaviors.receive {
      case (_, GetAccessToken(replyTo)) if expiry.isAfter(Instant.now().plus(1, ChronoUnit.MINUTES)) =>
        replyTo ! StatusReply.Success(token)
        Behaviors.same
      case (ctx, GetAccessToken(replyTo)) =>
        log.debug("Access token expired at {}, fetching new one", expiry)
        fetchAccessToken(ctx)
        loading(Seq(replyTo))
      case (_, msg) =>
        log.debug("Unexpected message while caching: {}", msg)
        Behaviors.same
    }

    private def fetchAccessToken(ctx: ActorContext[Protocol]): Unit = {
      import ctx.executionContext
      authClient
        .createAccessToken()
        .addHeader("Authorization", s"Bearer $refreshToken")
        .setDeadline(requestTimeout)
        .invoke(CreateAccessTokenRequest())
        .onComplete {
          case Success(token) =>
            ctx.self ! AccessToken(
              token.token,
              token.expireTime
                .map(_.asJavaInstant)
                .getOrElse(Instant.now().plus(30, ChronoUnit.MINUTES)))
          case Failure(exception) =>
            ctx.self ! GetFailed(exception)
        }
    }
  }

}
