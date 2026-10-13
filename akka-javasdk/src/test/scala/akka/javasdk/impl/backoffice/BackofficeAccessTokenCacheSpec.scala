/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.backoffice

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.grpc.GrpcClientSettings
import akka.grpc.GrpcServiceException
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import com.google.protobuf.timestamp.Timestamp
import com.typesafe.config.ConfigFactory
import io.grpc.Status
import kalix.api.auth.v1.auth.AccessToken
import kalix.api.auth.v1.auth.Auth
import kalix.api.auth.v1.auth.AuthHandler
import kalix.api.auth.v1.auth.CreateAccessTokenRequest
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BackofficeAccessTokenCacheSpec extends AnyWordSpec with Matchers with ScalaFutures with Eventually {

  private val config = ConfigFactory.parseString("""
    akka.http.server.enable-http2 = on
    akka.javasdk.dev-mode.backoffice.request-timeout = 2s
    """)

  private val refreshToken = "kxr_test-refresh-token"

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 20.millis)

  private def timestamp(instant: Instant) = Timestamp(instant.getEpochSecond, instant.getNano)

  private class FakeAuthServer(testKit: ActorTestKit) {
    val requestCount = new AtomicInteger()
    val authorizationHeaders = new CopyOnWriteArrayList[String]()
    @volatile var respond: Int => Future[AccessToken] = n => Future.successful(token(s"token-$n"))

    def token(value: String, expiry: Instant = Instant.now().plusSeconds(3600)): AccessToken =
      AccessToken(subject = "test", token = value, expireTime = Some(timestamp(expiry)))

    private val service = new Auth {
      override def createAccessToken(in: CreateAccessTokenRequest): Future[AccessToken] =
        respond(requestCount.incrementAndGet())
    }

    private val grpcHandler: HttpRequest => Future[HttpResponse] = AuthHandler(service)(testKit.system)
    private val handler: HttpRequest => Future[HttpResponse] = { request =>
      request.headers.find(_.lowercaseName == "authorization").foreach(h => authorizationHeaders.add(h.value))
      grpcHandler(request)
    }

    private val binding =
      Http()(testKit.system).newServerAt("127.0.0.1", 0).bind(handler).futureValue

    val clientSettings: GrpcClientSettings =
      GrpcClientSettings.connectToServiceAt("127.0.0.1", binding.localAddress.getPort)(testKit.system).withTls(false)

    def stop(): Unit = binding.unbind().futureValue
  }

  private def withCache(test: (BackofficeAccessTokenCache, FakeAuthServer) => Unit): Unit = {
    val testKit = ActorTestKit(config)
    try {
      val server = new FakeAuthServer(testKit)
      try {
        val cache = BackofficeAccessTokenCache(testKit.system)
        cache.init(server.clientSettings, refreshToken)
        test(cache, server)
      } finally server.stop()
    } finally testKit.shutdownTestKit()
  }

  "BackofficeAccessTokenCache" should {

    "fail when used before it is initialized" in {
      val testKit = ActorTestKit(config)
      try {
        val cache = BackofficeAccessTokenCache(testKit.system)
        cache.accessToken().failed.futureValue.getMessage should include("until it has been initialized")
      } finally testKit.shutdownTestKit()
    }

    "fetch an access token using the refresh token as a bearer token" in withCache { (cache, server) =>
      cache.accessToken().futureValue shouldBe "token-1"
      server.authorizationHeaders.asScala.toList shouldBe List(s"Bearer $refreshToken")
    }

    "cache an access token that is not close to expiry" in withCache { (cache, server) =>
      cache.accessToken().futureValue shouldBe "token-1"
      cache.accessToken().futureValue shouldBe "token-1"
      cache.accessToken().futureValue shouldBe "token-1"
      server.requestCount.get() shouldBe 1
    }

    "cache an access token without an expiry time" in withCache { (cache, server) =>
      server.respond = n => Future.successful(AccessToken(token = s"token-$n"))
      cache.accessToken().futureValue shouldBe "token-1"
      cache.accessToken().futureValue shouldBe "token-1"
      server.requestCount.get() shouldBe 1
    }

    "fetch a new access token when the cached one expires within a minute" in withCache { (cache, server) =>
      server.respond = n => Future.successful(server.token(s"token-$n", Instant.now().plusSeconds(30)))
      cache.accessToken().futureValue shouldBe "token-1"
      cache.accessToken().futureValue shouldBe "token-2"
      server.requestCount.get() shouldBe 2
    }

    "share one fetch between concurrent requests" in withCache { (cache, server) =>
      val release = Promise[AccessToken]()
      server.respond = _ => release.future
      val results = (1 to 5).map(_ => cache.accessToken())
      // Wait until the server has seen the request, then give the other asks time to queue up
      eventually(server.requestCount.get() shouldBe 1)
      Thread.sleep(200)
      release.success(server.token("shared"))
      results.foreach(_.futureValue shouldBe "shared")
      server.requestCount.get() shouldBe 1
    }

    "propagate a fetch failure to all waiting requests and retry on the next request" in withCache { (cache, server) =>
      server.respond = {
        case 1 => Future.failed(new GrpcServiceException(Status.UNAUTHENTICATED.withDescription("bad token")))
        case n => Future.successful(server.token(s"token-$n"))
      }
      cache.accessToken().failed.futureValue shouldBe a[Exception]
      cache.accessToken().futureValue shouldBe "token-2"
      server.requestCount.get() shouldBe 2
    }
  }
}
