/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.javasdk.JsonSupport
import akka.javasdk.a2a.A2aClientException
import akka.pattern.{ after => afterDelay }
import akka.stream.SystemMaterializer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class A2aOAuth2TokenCacheSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with LogCapturing {

  private implicit val ec: scala.concurrent.ExecutionContext = system.executionContext
  private implicit val materializer: akka.stream.Materializer = SystemMaterializer(system).materializer

  private val fetches = new AtomicInteger()
  private val lastForm = new AtomicReference[Map[String, String]](Map.empty)
  private val lastAuthorization = new AtomicReference[Option[String]](None)
  @volatile private var expiresIn: Option[String] = Some("3600")
  @volatile private var tokenDelay: FiniteDuration = Duration.Zero
  @volatile private var failWith: Option[String] = None

  private val http = Http()(system.classicSystem)

  private val tokenEndpoint = Route.seal {
    path("token") {
      post {
        (optionalHeaderValueByName("Authorization") & formFieldMap) { (authorization, form) =>
          lastAuthorization.set(authorization)
          lastForm.set(form)
          val n = fetches.incrementAndGet()
          val answer: Future[HttpResponse] = afterDelay(tokenDelay)(Future.successful(failWith match {
            case Some(error) =>
              HttpResponse(
                StatusCodes.Unauthorized,
                entity = HttpEntity(ContentTypes.`application/json`, s"""{"error":"$error"}"""))
            case None =>
              val expiry = expiresIn.fold("")(e => s""","expires_in":$e""")
              HttpResponse(entity = HttpEntity(
                ContentTypes.`application/json`,
                s"""{"access_token":"token-$n","token_type":"Bearer"$expiry}"""))
          }))(system.classicSystem)
          complete(answer)
        }
      }
    }
  }

  private val binding =
    Await.result(Http()(system.classicSystem).newServerAt("127.0.0.1", 0).bind(tokenEndpoint), 10.seconds)
  private val tokenUrl = s"http://127.0.0.1:${binding.localAddress.getPort}/token"

  override protected def afterAll(): Unit = {
    binding.unbind()
    super.afterAll()
  }

  @volatile private var clock = Instant.parse("2026-01-01T00:00:00Z")

  private def cache(
      scope: Option[String] = None,
      authMethod: A2aAuth.TokenEndpointAuthMethod = A2aAuth.ClientSecretBasic,
      clientId: String = "my-client",
      clientSecret: String = "my-secret") =
    new A2aOAuth2TokenCache(
      A2aAuth.OAuth2ClientCredentials(tokenUrl, clientId, clientSecret, scope, authMethod),
      http.singleRequest(_),
      JsonSupport.getObjectMapper,
      () => clock)

  private def reset(): Unit = {
    fetches.set(0)
    expiresIn = Some("3600")
    tokenDelay = Duration.Zero
    failWith = None
    clock = Instant.parse("2026-01-01T00:00:00Z")
  }

  private def get(c: A2aOAuth2TokenCache): String = Await.result(c.token(), 10.seconds)

  private def base64(s: String): String =
    java.util.Base64.getEncoder.encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8))

  "The token cache" should {

    "send the client id and secret in a Basic header by default" in {
      reset()
      get(cache(Some("claims.read")))
      lastForm.get() shouldBe Map("grant_type" -> "client_credentials", "scope" -> "claims.read")
      lastAuthorization.get() shouldBe Some("Basic " + base64("my-client:my-secret"))
    }

    "form-encode the client id and secret before the Basic encoding" in {
      reset()
      get(cache(clientId = "a b:c", clientSecret = "p%s+/:"))
      lastAuthorization.get() shouldBe Some("Basic " + base64("a+b%3Ac:p%25s%2B%2F%3A"))
    }

    "post the client id and secret in the form when configured" in {
      reset()
      get(cache(Some("claims.read"), A2aAuth.ClientSecretPost))
      lastForm.get() shouldBe Map(
        "grant_type" -> "client_credentials",
        "client_id" -> "my-client",
        "client_secret" -> "my-secret",
        "scope" -> "claims.read")
      lastAuthorization.get() shouldBe None
    }

    "leave the scope out when none is configured" in {
      reset()
      get(cache())
      lastForm.get().keySet should not contain "scope"
    }

    "reuse a token until shortly before it expires" in {
      reset()
      val c = cache()
      get(c) shouldBe "token-1"
      clock = clock.plusSeconds(3600 - 31)
      get(c) shouldBe "token-1"
      fetches.get() shouldBe 1
    }

    "fetch a new token shortly before the old one expires" in {
      reset()
      val c = cache()
      get(c) shouldBe "token-1"
      clock = clock.plusSeconds(3600 - 29)
      get(c) shouldBe "token-2"
      fetches.get() shouldBe 2
    }

    "refresh early, by half the lifetime at most, when the lifetime is short" in {
      reset()
      expiresIn = Some("10")
      val c = cache()
      get(c) shouldBe "token-1"
      clock = clock.plusSeconds(6)
      get(c) shouldBe "token-2"
    }

    "assume five minutes when the endpoint gives no lifetime" in {
      reset()
      expiresIn = None
      val c = cache()
      get(c)
      clock = clock.plusSeconds(260)
      get(c) shouldBe "token-1"
      clock = clock.plusSeconds(20)
      get(c) shouldBe "token-2"
    }

    "read a lifetime that the endpoint sends as a string" in {
      reset()
      expiresIn = Some("\"120\"")
      val c = cache()
      get(c)
      clock = clock.plusSeconds(85)
      get(c) shouldBe "token-1"
      clock = clock.plusSeconds(10)
      get(c) shouldBe "token-2"
    }

    "cap a lifetime too long to represent at one day" in {
      reset()
      expiresIn = Some("99999999999999")
      val c = cache()
      get(c) shouldBe "token-1"
      clock = clock.plusSeconds(24 * 3600 - 31)
      get(c) shouldBe "token-1"
      clock = clock.plusSeconds(2)
      get(c) shouldBe "token-2"
    }

    "share one request among concurrent callers" in {
      reset()
      tokenDelay = 300.millis
      val c = cache()
      val tokens = Await.result(Future.sequence(Seq.fill(10)(c.token())), 10.seconds)
      tokens.toSet shouldBe Set("token-1")
      fetches.get() shouldBe 1
    }

    "drop a rejected token and fetch another" in {
      reset()
      val c = cache()
      val first = get(c)
      c.invalidate(first)
      get(c) shouldBe "token-2"
    }

    "keep a token that is not the rejected one" in {
      reset()
      val c = cache()
      val first = get(c)
      c.invalidate("some other token")
      get(c) shouldBe first
    }

    "fail with the OAuth error code and without the secret" in {
      reset()
      failWith = Some("invalid_client")
      val e = intercept[A2aClientException](get(cache()))
      e.getMessage should include("401")
      e.getMessage should include("invalid_client")
      (e.getMessage should not).include("my-secret")
    }

    "ask again after a failure" in {
      reset()
      failWith = Some("temporarily_unavailable")
      val c = cache()
      intercept[A2aClientException](get(c))
      failWith = None
      get(c) shouldBe "token-2"
    }
  }
}
