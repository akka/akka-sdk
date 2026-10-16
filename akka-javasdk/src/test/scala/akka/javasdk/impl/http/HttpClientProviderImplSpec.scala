/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.javadsl.model.HttpRequest
import akka.javasdk.http.HttpClientProvider
import akka.javasdk.impl.Settings
import akka.runtime.sdk.spi.DeploymentInfo
import akka.runtime.sdk.spi.SpiSpiffeContext
import akka.util.ByteString
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.{ Context => OtelContext }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.jdk.CollectionConverters._

class HttpClientProviderImplSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with LogCapturing {

  private val traceContext: OtelContext = Span
    .wrap(
      SpanContext
        .create("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", TraceFlags.getSampled, TraceState.getDefault))
    .storeInContext(OtelContext.root())

  private def provider(deploymentInfo: DeploymentInfo = new DeploymentInfo("abc", "abc", None)) =
    new HttpClientProviderImpl(
      system,
      None,
      None,
      Settings(devModeSettings = None),
      deploymentInfo,
      system.executionContext)

  private def request(provider: HttpClientProvider, serviceName: String): HttpRequest =
    provider.httpClientFor(serviceName).GET("/path").asInstanceOf[RequestBuilderImpl[ByteString]].request

  private def headerNames(req: HttpRequest): Seq[String] =
    req.getHeaders.asScala.map(_.lowercaseName).toSeq

  "The HTTP client provider in production" should {

    "reach a service in this project by its bare name" in {
      request(provider(), "my-service").getUri.toString shouldBe "http://my-service/path"
    }

    "reach a service in a platform service in the platform service namespace" in {
      request(provider().forPlatformService("my-platform-service"), "platform-api").getUri.toString shouldBe
      "http://platform-api.abc-my-platform-service.svc.cluster.local/path"
    }

    "reject a URL or a host name for a platform service" in {
      val scoped = provider().forPlatformService("my-platform-service")
      Seq("https://example.com", "http://other", "foo.example.com", "localhost:9000").foreach { name =>
        withClue(s"[$name]: ") {
          intercept[IllegalArgumentException](scoped.httpClientFor(name)).getMessage should include(
            "only accepts a bare service name")
        }
      }
    }

    "fail for a platform service without a project id" in {
      val scoped = provider(new DeploymentInfo("", "", None)).forPlatformService("my-platform-service")
      intercept[IllegalArgumentException](scoped.httpClientFor("platform-api")).getMessage should include(
        "no project id known")
    }

    "reach the own platform service by bare name from a service of that platform service" in {
      val inPlatformService = provider(new DeploymentInfo("abc", "abc-ps", Some("ps")))
      request(inPlatformService.forPlatformService("ps"), "other").getUri.toString shouldBe "http://other/path"
      intercept[IllegalArgumentException](inPlatformService.forPlatformService("other-ps"))
    }

    "keep the trace headers but leave out the caller identity for a platform service, in any order" in {
      val callerFirst = provider()
        .withCallerSpiffeHeader("spiffe://caller")
        .withTelemetryContext(traceContext)
        .forPlatformService("my-platform-service")
      val scopeFirst = provider()
        .forPlatformService("my-platform-service")
        .asInstanceOf[HttpClientProviderImpl]
        .withCallerSpiffeHeader("spiffe://caller")
        .withTelemetryContext(traceContext)

      Seq(callerFirst, scopeFirst).foreach { scoped =>
        val names = headerNames(request(scoped, "platform-api"))
        names should contain("traceparent")
        names should not contain SpiSpiffeContext.CallerSpiffeHeaderName.toLowerCase
      }

      val unscoped = provider().withCallerSpiffeHeader("spiffe://caller")
      headerNames(request(unscoped, "my-service")) should contain(SpiSpiffeContext.CallerSpiffeHeaderName.toLowerCase)
    }
  }
}
