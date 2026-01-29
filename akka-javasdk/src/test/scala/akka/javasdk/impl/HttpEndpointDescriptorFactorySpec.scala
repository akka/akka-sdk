/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.StatusCodes.Forbidden
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.model.StatusCodes.Unauthorized
import akka.javasdk.impl.http.TestEndpoints.TestEndpointJwtClassAndMethodLevel
import akka.javasdk.impl.http.TestEndpoints.TestEndpointJwtOnlyMethodLevel
import akka.runtime.sdk.spi.All
import akka.runtime.sdk.spi.ClaimPattern
import akka.runtime.sdk.spi.ClaimValues
import akka.runtime.sdk.spi.HttpEndpointMethodSpec
import akka.runtime.sdk.spi.Internet
import akka.runtime.sdk.spi.ServiceNamePattern
import akka.runtime.sdk.spi.SpiJsonSchema
import akka.runtime.sdk.spi.StaticClaim
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object HttpEndpointDescriptorFactorySpec {
  object Spec {
    val empty = Spec()

    def apply(
        parameters: Seq[HttpEndpointMethodSpec.Parameter] = Seq.empty,
        requestBody: Option[HttpEndpointMethodSpec.RequestBody] = None): HttpEndpointMethodSpec =
      new HttpEndpointMethodSpec(parameters, requestBody)

    def parameter(name: String, schema: SpiJsonSchema.JsonSchemaDataType): HttpEndpointMethodSpec.Parameter =
      new HttpEndpointMethodSpec.PathParameter(name, schema)

    def jsonBody(schema: SpiJsonSchema.JsonSchemaDataType): Option[HttpEndpointMethodSpec.RequestBody] =
      Some(new HttpEndpointMethodSpec.JsonRequestBody(schema))

    def textBody(description: String = null): Option[HttpEndpointMethodSpec.RequestBody] =
      Some(new HttpEndpointMethodSpec.TextRequestBody(Option(description)))

    def lowLevelBody(
        description: String = null,
        required: Boolean = false): Option[HttpEndpointMethodSpec.RequestBody] =
      Some(new HttpEndpointMethodSpec.LowLevelRequestBody(Option(description), required))
  }
}

class HttpEndpointDescriptorFactorySpec extends AnyWordSpec with Matchers {
  import HttpEndpointDescriptorFactorySpec._
  import JsonSchemaSpec._

  "The HttpEndpointDescriptorFactory" should {
    "parse annotations on an endpoint class into a descriptor" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpoint], _ => null)
      val byMethodName = descriptor.methods.map(md => md.userMethod.getName -> md).toMap

      descriptor.mainPath should ===(Some("/prefix/"))
      descriptor.methods should have size 6
      descriptor.componentOptions.aclOpt shouldBe empty
      descriptor.componentOptions.jwtOpt shouldBe empty

      val itParameter = Spec.parameter("it", Schema.string())
      val aThingJsonBody = Spec.jsonBody(
        Schema.jsonObject(properties = Map("someProperty" -> Schema.string()), required = Seq("someProperty")))

      val list = byMethodName("list")
      list.pathExpression should ===("")
      list.httpMethod should ===(HttpMethods.GET)
      list.methodSpec should ===(Spec.empty)

      val get = byMethodName("get")
      get.pathExpression should ===("{it}")
      get.httpMethod should ===(HttpMethods.GET)
      get.methodSpec should ===(Spec(parameters = Seq(itParameter)))

      val create = byMethodName("create")
      create.pathExpression should ===("{it}")
      create.httpMethod should ===(HttpMethods.POST)
      create.methodSpec should ===(Spec(parameters = Seq(itParameter), requestBody = aThingJsonBody))

      val delete = byMethodName("delete")
      delete.pathExpression should ===("{it}")
      delete.httpMethod should ===(HttpMethods.DELETE)
      delete.methodSpec should ===(Spec(parameters = Seq(itParameter)))

      val update = byMethodName("update")
      update.pathExpression should ===("{it}")
      update.httpMethod should ===(HttpMethods.PUT)
      update.methodSpec should ===(Spec(parameters = Seq(itParameter), requestBody = aThingJsonBody))

      val patch = byMethodName("patch")
      patch.pathExpression should ===("{it}")
      patch.httpMethod should ===(HttpMethods.PATCH)
      patch.methodSpec should ===(Spec(parameters = Seq(itParameter), requestBody = aThingJsonBody))
    }

    "create endpoint method specs" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpointSpecs], _ => null)
      val byMethodName = descriptor.methods.map(md => md.userMethod.getName -> md).toMap

      descriptor.mainPath shouldBe Some("/test-specs/")
      descriptor.methods should have size 9

      val parametersOnly = byMethodName("parametersOnly")
      parametersOnly.pathExpression shouldBe "parameters-only/{someString}/and/{someInt}"
      parametersOnly.httpMethod shouldBe HttpMethods.GET
      parametersOnly.methodSpec shouldBe Spec(parameters = Seq(
        Spec.parameter("someString", Schema.string("some string")),
        Spec.parameter("someInt", Schema.integer("some int"))))

      def someObjectBody(description: String = null): Option[HttpEndpointMethodSpec.RequestBody] =
        Spec.jsonBody(
          Schema.jsonObject(
            description = description,
            properties = Map(
              "someString" -> Schema.string("some string"),
              "optionalString" -> Schema.string(),
              "someBoolean" -> Schema.boolean(),
              "someInt" -> Schema.integer(),
              "optionalDouble" -> Schema.number("optional double")),
            required = Seq("someBoolean", "someInt", "someString")))

      val parametersAndBody = byMethodName("parametersAndBody")
      parametersAndBody.pathExpression shouldBe "parameters-and-body/{someString}/and/{someDouble}"
      parametersAndBody.httpMethod shouldBe HttpMethods.POST
      parametersAndBody.methodSpec shouldBe Spec(
        parameters = Seq(Spec.parameter("someString", Schema.string()), Spec.parameter("someDouble", Schema.number())),
        requestBody = someObjectBody())

      val bodyOnly = byMethodName("bodyOnly")
      bodyOnly.pathExpression shouldBe "body-only"
      bodyOnly.httpMethod shouldBe HttpMethods.POST
      bodyOnly.methodSpec shouldBe Spec(requestBody = someObjectBody("some body"))

      val parametersAndTextBody = byMethodName("parametersAndTextBody")
      parametersAndTextBody.pathExpression shouldBe "parameters-and-text-body/{someInt}"
      parametersAndTextBody.httpMethod shouldBe HttpMethods.POST
      parametersAndTextBody.methodSpec shouldBe Spec(
        parameters = Seq(Spec.parameter("someInt", Schema.integer())),
        requestBody = Spec.textBody("some body"))

      val textBodyOnly = byMethodName("textBodyOnly")
      textBodyOnly.pathExpression shouldBe "text-body-only"
      textBodyOnly.httpMethod shouldBe HttpMethods.POST
      textBodyOnly.methodSpec shouldBe Spec(requestBody = Spec.textBody("some body"))

      val parametersAndArrayBody = byMethodName("parametersAndArrayBody")
      parametersAndArrayBody.pathExpression shouldBe "parameters-and-array-body/{someString}/and/{someInt}"
      parametersAndArrayBody.httpMethod shouldBe HttpMethods.POST
      parametersAndArrayBody.methodSpec shouldBe Spec(
        parameters = Seq(Spec.parameter("someString", Schema.string()), Spec.parameter("someInt", Schema.integer())),
        requestBody = Spec.jsonBody(Schema.array(Schema.string())))

      val arrayBodyOnly = byMethodName("arrayBodyOnly")
      arrayBodyOnly.pathExpression shouldBe "array-body-only"
      arrayBodyOnly.httpMethod shouldBe HttpMethods.POST
      arrayBodyOnly.methodSpec shouldBe Spec(requestBody = Spec.jsonBody(Schema.array(Schema.number(), "some body")))

      val parametersAndLowLevelBody = byMethodName("parametersAndLowLevelBody")
      parametersAndLowLevelBody.pathExpression shouldBe "parameters-and-low-level-body/{someBoolean}"
      parametersAndLowLevelBody.httpMethod shouldBe HttpMethods.POST
      parametersAndLowLevelBody.methodSpec shouldBe Spec(
        parameters = Seq(Spec.parameter("someBoolean", Schema.boolean("some boolean"))),
        requestBody = Spec.lowLevelBody("some body", required = true))

      val lowLevelBodyOnly = byMethodName("lowLevelBodyOnly")
      lowLevelBodyOnly.pathExpression shouldBe "low-level-body-only"
      lowLevelBodyOnly.httpMethod shouldBe HttpMethods.POST
      lowLevelBodyOnly.methodSpec shouldBe Spec(requestBody = Spec.lowLevelBody())
    }

    "fail when path expression does not match parameters" in {
      val message = intercept[ValidationException] {
        HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.InvalidEndpointMethods], _ => null)
      }.getMessage

      message should include(
        "There are more parameters in the path expression [/{id}/my-endpoint/] than there are parameters for [akka.javasdk.impl.http.TestEndpoints$InvalidEndpointMethods.list1]")
      message should include(
        "The parameter [id] in the path expression [/{id}/my-endpoint/] does not match the method parameter name [bob] for [akka.javasdk.impl.http.TestEndpoints$InvalidEndpointMethods.list2]")
      message should include(
        "The parameter [bob] in the path expression [/{id}/my-endpoint/something/{bob}] does not match the method parameter name [value] for [akka.javasdk.impl.http.TestEndpoints$InvalidEndpointMethods.list3]")
      message should include(
        "There are [2] parameters ([value,body]) for endpoint method [akka.javasdk.impl.http.TestEndpoints$InvalidEndpointMethods.list5] not matched by the path expression")
      message should include(
        "Wildcard path can only be the last segment of the path [/{id}/my-endpoint/wildcard/**/not/last]")
    }

    "hide double slash when combining prefix with method path" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.WithRootPrefix], _ => null)
      val byMethodName = descriptor.methods.map(md => md.userMethod.getName -> md).toMap
      byMethodName("root").pathExpression shouldEqual ""
      byMethodName("a").pathExpression shouldEqual "a"
      byMethodName("b").pathExpression shouldEqual "b"
    }

    "parse ACL annotations into descriptor" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpointAcls], _ => null)

      descriptor.mainPath should ===(Some("/acls/"))
      descriptor.methods should have size 3

      descriptor.componentOptions.aclOpt should not be empty
      descriptor.componentOptions.aclOpt.get.deny shouldBe List(All)
      descriptor.componentOptions.aclOpt.get.denyHttpCode should contain(NotFound)

      val byMethodName = descriptor.methods.map(md => md.userMethod.getName -> md).toMap

      val get = byMethodName("secret")
      get.methodOptions.acl should not be empty
      get.methodOptions.acl.get.allow.collect { case p: ServiceNamePattern => p.pattern } shouldEqual Seq(
        "backoffice-service")
      get.methodOptions.acl.get.deny shouldBe List(Internet)
      get.methodOptions.acl.get.denyHttpCode should contain(Unauthorized)

      val noAcl = byMethodName("noAcl")
      noAcl.methodOptions.acl shouldBe empty

      val thisAndThat = byMethodName("thisAndThat")
      thisAndThat.methodOptions.acl should not be empty
      thisAndThat.methodOptions.acl.get.allow.collect { case p: ServiceNamePattern => p.pattern } shouldBe Seq(
        "this",
        "that")
      thisAndThat.methodOptions.acl.get.deny shouldBe empty
      thisAndThat.methodOptions.acl.get.denyHttpCode should contain(Forbidden)
    }

    "throw error if annotations are not valid" in {
      assertThrows[IllegalArgumentException] {
        HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpointInvalidAcl], _ => null)
      }

      val caught = intercept[IllegalArgumentException] {
        HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpointInvalidAclDenyCode], _ => null)
      }
      caught.getMessage should include("Invalid HTTP status code: 123123")
    }

    //Utility to compare StaticClaim to avoid creating `equals` in the original.
    implicit class ClaimValuesWrapper(staticClaim: StaticClaim) {
      override def equals(obj: Any): Boolean = obj match {
        case sc: StaticClaim if sc.name == staticClaim.name =>
          (sc.content, staticClaim.content) match {
            case (cv0: ClaimValues, cv1: ClaimValues)   => cv0.content == cv1.content
            case (cp0: ClaimPattern, cp1: ClaimPattern) => cp0.content == cp1.content
            case _                                      => false
          }
        case _ => false
      }
      def toWrapper: ClaimValuesWrapper = this
    }

    "parse JWT class level annotations into the descriptor" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.TestEndpointJwtClassLevel], _ => null)
      val jwtComponentOptions = descriptor.componentOptions.jwtOpt.get

      jwtComponentOptions.validate shouldBe true
      jwtComponentOptions.bearerTokenIssuers shouldBe List("a", "b")
      (jwtComponentOptions.claims.map(_.toWrapper).toList should contain).allOf(
        new StaticClaim("aud", new ClaimValues(Set("value1.kalix.io"))),
        new StaticClaim("roles", new ClaimValues(Set("viewer", "editor"))),
        new StaticClaim("sub", new ClaimPattern("^sub-\\S+$")))

      descriptor.methods.head.methodOptions.jwtOpt shouldBe None
    }

    "parse JWT class and method level annotations into the descriptor" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[TestEndpointJwtClassAndMethodLevel], _ => null)
      val jwtComponentOptions = descriptor.componentOptions.jwtOpt.get

      jwtComponentOptions.validate shouldBe true
      jwtComponentOptions.bearerTokenIssuers shouldBe List("a", "b")
      (jwtComponentOptions.claims.map(_.toWrapper).toList should contain).allOf(
        new StaticClaim("aud", new ClaimValues(Set("value1.kalix.value2.io"))),
        new StaticClaim("roles", new ClaimValues(Set("viewer", "editor"))),
        new StaticClaim("sub", new ClaimPattern("^sub-\\S+$")))

      val jwtMethodOptions = descriptor.methods.head.methodOptions.jwtOpt.get
      jwtMethodOptions.validate shouldBe true
      jwtMethodOptions.bearerTokenIssuers shouldBe List("c", "d")
      (jwtMethodOptions.claims.map(_.toWrapper).toList should contain).allOf(
        new StaticClaim("aud", new ClaimValues(Set("value1.kalix.dev"))),
        new StaticClaim("roles", new ClaimValues(Set("admin"))),
        new StaticClaim("sub", new ClaimPattern("^-\\S+$")))
    }

    "parse JWT only method level annotations into the descriptor" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[TestEndpointJwtOnlyMethodLevel], _ => null)
      val jwtComponentOptions = descriptor.componentOptions.jwtOpt

      jwtComponentOptions shouldBe None

      val jwtMethodOptions = descriptor.methods.head.methodOptions.jwtOpt.get
      jwtMethodOptions.validate shouldBe true
      jwtMethodOptions.bearerTokenIssuers shouldBe List("c", "d")

      (jwtMethodOptions.claims.map(_.toWrapper).toList should contain).allOf(
        new StaticClaim("aud", new ClaimValues(Set("value1.kalix.dev"))),
        new StaticClaim("roles", new ClaimValues(Set("admin"))),
        new StaticClaim("sub", new ClaimPattern("^-\\S+$")))
    }

    "complain if an ENV is missing in component level" in {
      val exception = intercept[IllegalArgumentException] {
        val valueClaimContent = "one-${ENV}-two-${ENV3}-three"
        HttpEndpointDescriptorFactory.extractEnvVars(
          valueClaimContent,
          "origin-ref") shouldBe "one-value1-two-value1-three"
      }
      exception.getMessage shouldBe "[ENV3] env var is missing but it is used in claim [one-${ENV}-two-${ENV3}-three] in [origin-ref]."
    }

    "parse valid WebSocket endpoints" in {
      val descriptor = HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.ValidWebSocketEndpoints], _ => null)
      val byMethodName = descriptor.methods.map(md => md.userMethod.getName -> md).toMap

      descriptor.mainPath should ===(Some("/websocket/"))
      descriptor.methods should have size 4

      val textWs = byMethodName("textWebSocket")
      textWs.pathExpression should ===("text")
      textWs.httpMethod should ===(HttpMethods.GET)
      textWs.webSocket shouldBe true

      val binaryWs = byMethodName("binaryWebSocket")
      binaryWs.pathExpression should ===("binary")
      binaryWs.webSocket shouldBe true

      val messageWs = byMethodName("messageWebSocket")
      messageWs.pathExpression should ===("message")
      messageWs.webSocket shouldBe true

      val withPathParam = byMethodName("withPathParam")
      withPathParam.pathExpression should ===("with-path-param/{id}")
      withPathParam.webSocket shouldBe true
    }

    "fail when WebSocket method has wrong return type" in {
      val message = intercept[ValidationException] {
        HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.InvalidWebSocketReturnType], _ => null)
      }.getMessage

      message should include("Wrong return type for WebSocket method")
      message should include("wrongReturnType")
      message should include("must be [akka.stream.javadsl.Flow]")
    }

    "fail when WebSocket method has different in/out message types" in {
      val message = intercept[ValidationException] {
        HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.InvalidWebSocketDifferentTypes], _ => null)
      }.getMessage

      message should include("differentInOut")
      message should include("has different types of Flow in and out messages")
    }

    "fail when WebSocket method has unsupported message type" in {
      val message = intercept[ValidationException] {
        HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.InvalidWebSocketMessageType], _ => null)
      }.getMessage

      message should include("unsupportedType")
      message should include("has unsupported message type")
      message should include("must be String for text messages")
    }

    "fail when WebSocket method has unsupported materialized value type" in {
      val message = intercept[ValidationException] {
        HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.InvalidWebSocketMatType], _ => null)
      }.getMessage

      message should include("wrongMatType")
      message should include("has unsupported materialized value type")
      message should include("must be akka.NotUsed")
    }

    "fail when WebSocket method has request body parameter" in {
      val message = intercept[ValidationException] {
        HttpEndpointDescriptorFactory(classOf[http.TestEndpoints.InvalidWebSocketBodyParam], _ => null)
      }.getMessage

      message should include("Request body parameter defined for WebSocket method")
      message should include("withBodyParam")
      message should include("this is not supported")
    }
  }
}
