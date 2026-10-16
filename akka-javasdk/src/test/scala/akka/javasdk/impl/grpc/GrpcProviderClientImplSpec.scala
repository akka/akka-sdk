/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.grpc

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.impl.Settings
import akka.runtime.sdk.spi.DeploymentInfo
import akka.runtime.sdk.spi.SpiSpiffeContext
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.{ Context => OtelContext }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.jdk.CollectionConverters._

class GrpcProviderClientImplSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with LogCapturing {

  "The gRPC client config" should {

    "filter out unsupported config" in {
      val result = GrpcClientProviderImpl.serviceConfigFor(
        "one.example.com",
        ConfigFactory.parseString("""
          "one.example.com" {
            host = "example.com"
            port = 8080
            use-tls = false
            service-discovery {
              mechanism = "custom"
            }
            load-balancing-policy = "round_robin"
            deadline = 5s
            override-authority = "aargh"
            user-agent = "My Service"
            trusted = "some/classpath/"
            ssl-provider = "openssl"
            connection-attempts = 5
            eager-connection = on
          }"""))

      result should ===(ConfigFactory.parseString("""
          "one.example.com" {
            host = "example.com"
            port = 8080
            use-tls = false
          }"""))
    }

    "should make an empty block if config missing" in {
      val result = GrpcClientProviderImpl.serviceConfigFor(
        "one.example.com",
        ConfigFactory.parseString("""
          "some-other.example.com" {
            host = "other.example.com"
            port = 9000
            use-tls = true
          }
        """))

      result.getConfig(""""one.example.com"""").isEmpty shouldBe true
    }

    "works for simple service name as well" in {
      // for dev/test mode
      val validConfig = ConfigFactory.parseString("""
          some-service {
            host = "other.example.com"
            port = 9000
            use-tls = true
          }
        """)
      val result = GrpcClientProviderImpl.serviceConfigFor("some-service", validConfig)

      result shouldEqual validConfig
    }

  }

  private val traceContext: OtelContext = Span
    .wrap(
      SpanContext
        .create("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", TraceFlags.getSampled, TraceState.getDefault))
    .storeInContext(OtelContext.root())

  private def provider(deploymentInfo: DeploymentInfo = new DeploymentInfo("abc", "abc", None)) =
    new GrpcClientProviderImpl(
      system,
      Settings(devModeSettings = None),
      ConfigFactory.parseString("akka.javasdk.grpc.client {}"),
      deploymentInfo,
      None)

  private val client = classOf[FakeGrpcClient]

  private def headerNames(client: FakeGrpcClient): Seq[String] =
    client.headers.asScala.map(_.getKey).toSeq

  "The gRPC client provider in production" should {

    "reach a service in this project by its bare name" in {
      provider().grpcClientFor(client, "my-service").settings.serviceName shouldBe "my-service"
    }

    "reach a service in a platform service in the platform service namespace" in {
      provider()
        .forPlatformService("my-platform-service")
        .grpcClientFor(client, "platform-api")
        .settings
        .serviceName shouldBe "platform-api.abc-my-platform-service.svc.cluster.local"
    }

    "cache a client per platform service" in {
      val p = provider()
      val projectClient = p.grpcClientFor(client, "platform-api")
      val platformClient = p.forPlatformService("my-platform-service").grpcClientFor(client, "platform-api")
      platformClient should not be theSameInstanceAs(projectClient)
      (p.grpcClientFor(client, "platform-api") should be).theSameInstanceAs(projectClient)
      (p.forPlatformService("my-platform-service").grpcClientFor(client, "platform-api") should be)
        .theSameInstanceAs(platformClient)
    }

    "reject a host name for a platform service" in {
      val scoped = provider().forPlatformService("my-platform-service")
      Seq("foo.example.com", "localhost:9000").foreach { name =>
        withClue(s"[$name]: ") {
          intercept[IllegalArgumentException](scoped.grpcClientFor(client, name)).getMessage should include(
            "only accepts a bare service name")
        }
      }
    }

    "fail for a platform service without a project id" in {
      val scoped = provider(new DeploymentInfo("", "", None)).forPlatformService("my-platform-service")
      intercept[IllegalArgumentException](scoped.grpcClientFor(client, "platform-api")).getMessage should include(
        "no project id known")
    }

    "share the bare name client for the own platform service in a service of that platform service" in {
      val p = provider(new DeploymentInfo("abc", "abc-ps", Some("ps")))
      (p.forPlatformService("ps").grpcClientFor(client, "other") should be)
        .theSameInstanceAs(p.grpcClientFor(client, "other"))
      intercept[IllegalArgumentException](p.forPlatformService("other-ps"))
    }

    "keep the trace headers but leave out the caller identity for a platform service" in {
      val scoped = provider()
        .withCallContext(Some(traceContext), Some("spiffe://caller"))
        .forPlatformService("my-platform-service")
      val names = headerNames(scoped.grpcClientFor(client, "platform-api"))
      names should contain("traceparent")
      names should not contain SpiSpiffeContext.CallerSpiffeHeaderName

      val unscoped = provider().withCallContext(Some(traceContext), Some("spiffe://caller"))
      headerNames(unscoped.grpcClientFor(client, "my-service")) should contain(SpiSpiffeContext.CallerSpiffeHeaderName)
    }
  }
}
