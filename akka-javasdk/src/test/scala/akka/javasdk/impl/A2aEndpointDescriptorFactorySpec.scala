/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.concurrent.ExecutionContext

import akka.javasdk.impl.a2a.TestA2aEndpoints
import akka.runtime.sdk.spi.A2aEndpointConstructionContext
import akka.runtime.sdk.spi.A2aEndpointDescriptor
import akka.runtime.sdk.spi.A2aEndpointDescriptor.DataPart
import akka.runtime.sdk.spi.A2aEndpointDescriptor.TextPart
import akka.runtime.sdk.spi.HttpRequestHeaders
import akka.runtime.sdk.spi.JwtClaims
import akka.runtime.sdk.spi.RequestPrincipal
import akka.runtime.sdk.spi.SpiSpiffeContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class A2aEndpointDescriptorFactorySpec extends AnyWordSpec with Matchers with ScalaFutures {

  private implicit val ec: ExecutionContext = ExecutionContext.parasitic

  private def claims(values: Map[String, String]): JwtClaims = new JwtClaims {
    override def getAllClaimNames: Seq[String] = values.keys.toSeq
    override def hasClaim(name: String): Boolean = values.contains(name)
    override def getIntClaim(name: String): Option[Int] = None
    override def getLongClaim(name: String): Option[Long] = None
    override def getDoubleClaim(name: String): Option[Double] = None
    override def getStringClaim(name: String): Option[String] = values.get(name)
    override def getStringClaims(name: String): Seq[String] = values.get(name).toSeq
    override def getBooleanClaim(name: String): Option[Boolean] = None
    override def getRawClaim(name: String): Option[String] = values.get(name)
  }

  private def context(
      jwtClaims: Option[JwtClaims] = None,
      service: Option[String] = None): A2aEndpointConstructionContext =
    new A2aEndpointConstructionContext {
      override def telemetryContext: Context = Context.root()
      override def openTelemetrySpan: Option[Span] = None
      override def principal: RequestPrincipal = new RequestPrincipal(None, service)
      override def jwt: Option[JwtClaims] = jwtClaims
      override def requestHeaders: HttpRequestHeaders = null
      override def spiffeContext: Option[SpiSpiffeContext] = None
    }

  private def message(contextId: Option[String], text: String = "hello") =
    new A2aEndpointDescriptor.Message("m1", contextId, None, Seq(new TextPart(text)), Map.empty)

  private lazy val claimsDescriptor = A2aEndpointDescriptorFactory(
    classOf[TestA2aEndpoints.ClaimsEndpoint],
    (_: A2aEndpointConstructionContext, call: A2aEndpointDescriptorFactory.CallContext) => {
      val endpoint = new TestA2aEndpoints.ClaimsEndpoint
      endpoint._internalSetRequestContext(new TestRequestContext(call))
      endpoint
    })

  private def reply(context: A2aEndpointConstructionContext, message: A2aEndpointDescriptor.Message): String =
    claimsDescriptor.handler(context, message).futureValue.parts.head.asInstanceOf[TextPart].text

  "The A2A endpoint descriptor factory" should {

    "describe the endpoint from the annotation" in {
      claimsDescriptor.endpointPath shouldBe "/a2a/claims"
      claimsDescriptor.implementationName shouldBe classOf[TestA2aEndpoints.ClaimsEndpoint].getName
      val card = claimsDescriptor.card
      card.name shouldBe "Claims"
      card.description shouldBe "Answers questions about claims"
      card.version shouldBe "1.2.3"
      card.provider.map(p => (p.organization, p.url)) shouldBe Some(("Example Inc", "https://example.com"))
      val skill = card.skills.head
      (skill.id, skill.name, skill.tags, skill.examples) shouldBe
      (("status", "Claim status", Seq("claims"), Seq("What is the status of claim 17?")))
      claimsDescriptor.componentOptions.aclOpt shouldBe defined
      claimsDescriptor.componentOptions.jwtOpt shouldBe defined
    }

    "default to the root path, no provider and no options" in {
      val descriptor = A2aEndpointDescriptorFactory(
        classOf[TestA2aEndpoints.RootEndpoint],
        (_: A2aEndpointConstructionContext, _: A2aEndpointDescriptorFactory.CallContext) =>
          new TestA2aEndpoints.RootEndpoint)
      descriptor.endpointPath shouldBe "/"
      descriptor.card.provider shouldBe None
      descriptor.card.skills shouldBe empty
      descriptor.componentOptions.aclOpt shouldBe None
      descriptor.componentOptions.jwtOpt shouldBe None
    }

    "fail for two skills with the same id" in {
      val exception = intercept[ValidationException] {
        A2aEndpointDescriptorFactory(
          classOf[TestA2aEndpoints.DuplicateSkillsEndpoint],
          (_: A2aEndpointConstructionContext, _: A2aEndpointDescriptorFactory.CallContext) =>
            new TestA2aEndpoints.DuplicateSkillsEndpoint)
      }
      exception.getMessage should include("same")
    }

    "give the endpoint the text of the message and send the context id in the reply" in {
      val jwt = Some(claims(Map("iss" -> "https://issuer.example.com", "sub" -> "alice")))
      val response = claimsDescriptor.handler(context(jwt), message(Some("ctx-1"))).futureValue
      response.contextId shouldBe Some("ctx-1")
      val text = response.parts.head.asInstanceOf[TextPart].text
      text should include("context=ctx-1")
      text should include("text=hello")
    }

    "generate a context id for the first message and send it in the reply" in {
      val response = claimsDescriptor.handler(context(), message(None)).futureValue
      val generated = response.contextId.get
      generated should not be empty
      response.parts.head.asInstanceOf[TextPart].text should include(s"context=$generated")
    }

    "scope the session by caller and context id" in {
      def session(jwt: Map[String, String], contextId: String) =
        reply(context(Some(claims(jwt))), message(Some(contextId))).split(",").head

      val alice = Map("iss" -> "https://issuer.example.com", "sub" -> "alice")
      val bob = Map("iss" -> "https://issuer.example.com", "sub" -> "bob")
      val aliceOtherIssuer = Map("iss" -> "https://other.example.com", "sub" -> "alice")

      session(alice, "ctx-1") shouldBe session(alice, "ctx-1")
      session(alice, "ctx-1") should not be session(alice, "ctx-2")
      session(alice, "ctx-1") should not be session(bob, "ctx-1")
      session(alice, "ctx-1") should not be session(aliceOtherIssuer, "ctx-1")
    }

    "scope the session of a calling service by its name" in {
      def session(service: String) = reply(context(service = Some(service)), message(Some("ctx-1"))).split(",").head
      session("billing") shouldBe session("billing")
      session("billing") should not be session("shipping")
    }

    "give an anonymous caller a new session for each request" in {
      def session() = reply(context(), message(Some("ctx-1"))).split(",").head
      session() should not be session()
    }

    "convert structured parts and metadata" in {
      val descriptor = A2aEndpointDescriptorFactory(
        classOf[TestA2aEndpoints.RootEndpoint],
        (_: A2aEndpointConstructionContext, _: A2aEndpointDescriptorFactory.CallContext) => {
          new TestA2aEndpoints.RootEndpoint {
            override def onMessage(message: akka.javasdk.a2a.A2aMessage): akka.javasdk.a2a.A2aReply = {
              message.parts().get(1) shouldBe an[akka.javasdk.a2a.A2aPart.DataPart]
              message.metadata().get("skillId") shouldBe "status"
              akka.javasdk.a2a.A2aReply.data(java.util.Map.of("answer", Integer.valueOf(42)))
            }
          }
        })
      val input = new A2aEndpointDescriptor.Message(
        "m1",
        None,
        None,
        Seq(new TextPart("a"), new DataPart(Map("k" -> "v"))),
        Map("skillId" -> "status"))
      val response = descriptor.handler(context(), input).futureValue
      response.parts.head.asInstanceOf[DataPart].data shouldBe Map("answer" -> 42)
    }

    "fail when the endpoint returns null" in {
      val descriptor = A2aEndpointDescriptorFactory(
        classOf[TestA2aEndpoints.RootEndpoint],
        (_: A2aEndpointConstructionContext, _: A2aEndpointDescriptorFactory.CallContext) =>
          new TestA2aEndpoints.RootEndpoint)
      descriptor.handler(context(), message(None)).failed.futureValue shouldBe an[IllegalStateException]
    }
  }
}
