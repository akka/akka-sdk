/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.http

import scala.concurrent.duration.DurationInt

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpMethods.DELETE
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.HttpMethods.PATCH
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.HttpMethods.PUT
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import kalix.javasdk.JsonSupport
import kalix.spring.testmodels.EndpointsTestModels
import kalix.spring.testmodels.EndpointsTestModels.DeleteHelloEndpoint
import kalix.spring.testmodels.EndpointsTestModels.GetHelloEndpoint
import kalix.spring.testmodels.EndpointsTestModels.PatchHelloEndpoint
import kalix.spring.testmodels.EndpointsTestModels.PostHelloEndpoint
import kalix.spring.testmodels.EndpointsTestModels.PutHelloEndpoint
import org.scalatest.OptionValues
import org.scalatest.concurrent.Futures
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HttpEndpointMethodRouterSpec
    extends AnyWordSpec
    with Matchers
    with Futures
    with OptionValues
    with ScalatestRouteTest {

  val methodRouter =
    HttpEndpointMethodRouter(classOf[GetHelloEndpoint], () => new GetHelloEndpoint) ++
    HttpEndpointMethodRouter(classOf[PostHelloEndpoint], () => new PostHelloEndpoint) ++
    HttpEndpointMethodRouter(classOf[PutHelloEndpoint], () => new PutHelloEndpoint) ++
    HttpEndpointMethodRouter(classOf[PatchHelloEndpoint], () => new PatchHelloEndpoint) ++
    HttpEndpointMethodRouter(classOf[DeleteHelloEndpoint], () => new DeleteHelloEndpoint)

  "HttpEndpointMethodRouter" should {

    "return None for non-existent path" in {
      // no matching method
      methodRouter.findMethod(GET, Path("/foo/bar/baz/qux")) shouldBe empty
    }

    "generate method invokers for a GET with path variable" in {
      val reqPath = "/hello/Joe"
      val (method, params) = methodRouter.findMethod(GET, Path(reqPath)).value
      method.javaMethod.getName shouldBe "name"
      method.invoke(params) shouldBe new EndpointsTestModels.Name("Joe")
    }

    "generate method invokers for a GET with two path variables" in {
      val reqPath = "/hello/Joe/20"
      val (method, params) = methodRouter.findMethod(GET, Path(reqPath)).value
      method.javaMethod.getName shouldBe "nameAndAge"
      method.invoke(params) shouldBe "name: Joe, age: 20"
    }

    "generate method invokers for a GET with path variables - fixed path gets precedence" in {
      pending // FIXME needs a tree structure, won't work with sorting based on toString
      val reqPath = "/hello/name/20"
      val (method, params) = methodRouter.findMethod(GET, Path(reqPath)).value
      method.javaMethod.getName shouldBe "fixedNameAndAge"
      method.invoke(params) shouldBe "name: fixed, age: 20"
    }

    "generate method invokers for a POST with body" in {
      val reqPath = "/hello"
      val (method, params) = methodRouter.findMethod(POST, Path(reqPath)).value
      method.javaMethod.getName shouldBe "namePost"
      method.invoke(params, new EndpointsTestModels.Name("Joe")) shouldBe "name: Joe"
    }

    "generate method invokers for a POST with path variable and body" in {
      val reqPath = "/hello/20"
      val (method, params) = methodRouter.findMethod(POST, Path(reqPath)).value
      method.javaMethod.getName shouldBe "nameAndAgePost"
      method.invoke(params, new EndpointsTestModels.Name("Joe")) shouldBe "name: Joe, age: 20"
    }

    "generate method invokers for a PUT with body" in {
      val reqPath = "/hello"
      val (method, params) = methodRouter.findMethod(PUT, Path(reqPath)).value
      method.javaMethod.getName shouldBe "helloPut"
      method.invoke(params, new EndpointsTestModels.Name("Joe")) shouldBe "name: Joe"
    }

    "generate method invokers for a PATCH with body" in {
      val reqPath = "/hello"
      val (method, params) = methodRouter.findMethod(PATCH, Path(reqPath)).value
      method.javaMethod.getName shouldBe "helloPatch"
      method.invoke(params, new EndpointsTestModels.Name("Joe")) shouldBe "name: Joe"
    }

    "generate method invokers for a DELETE with body" in {
      val reqPath = "/hello"
      val (method, params) = methodRouter.findMethod(DELETE, Path(reqPath)).value
      method.javaMethod.getName shouldBe "helloDelete"
      method.invoke(params).asInstanceOf[AnyRef] shouldBe null
    }

  }

  "HttpEndpointMethodRouter as Akka Http route" should {

    val partialFunction = (req: HttpRequest) =>
      methodRouter
        .route(10.seconds)
        .applyOrElse(req, (_: HttpRequest) => throw new NoSuchElementException("No route defined for this request."))

    val route: Route = {
      extractRequest { request =>
        onSuccess(partialFunction(request)) { response => complete(response) }
      }
    }

    "invoke java method for GET request with path variable and Json return" in {
      Get("/hello/Joe") ~> route ~> check {
        val body = response.entity.toStrict(3.seconds).futureValue.data.utf8String

        response.status shouldBe StatusCodes.OK
        val nameResponse = JsonSupport.getObjectMapper.readValue(body, classOf[EndpointsTestModels.Name])
        nameResponse.value() shouldEqual "Joe"
      }
    }

    "invoke java method for GET request with two path variables" in {
      Get("/hello/Joe/20") ~> route ~> check {
        responseAs[String] shouldEqual "name: Joe, age: 20"
      }
    }

    "invoke java method for GET request with two path variables and HttpRequest return" in {
      Get("/hello/Joe/20/http-response") ~> route ~> check {
        val body = response.entity.toStrict(3.seconds).futureValue.data.utf8String
        body shouldEqual "http => name: Joe, age: 20"
      }
    }

    "invoke java method for GET request with two path variables and async return" in {
      Get("/hello/Joe/20/async") ~> route ~> check {
        responseAs[String] shouldEqual "async => name: Joe, age: 20"
      }
    }

    "invoke java method for GET request with two path variables and async HttpRequest return" in {
      Get("/hello/Joe/20/async/http-response") ~> route ~> check {
        val body = response.entity.toStrict(3.seconds).futureValue.data.utf8String
        body shouldEqual "async http => name: Joe, age: 20"
      }
    }

    "invoke java method on a POST request with one path variable and body" in {
      val jsonRequest = ByteString(s"""
           |{
           |  "value": "Joe"
           |}
        """.stripMargin)

      // create a POST request
      val request = HttpRequest(
        HttpMethods.POST,
        uri = "/hello/20",
        entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

      request ~> Route.seal(route) ~> check {
        responseAs[String] shouldEqual "name: Joe, age: 20"
      }
    }

    "invoke java method on a POST request with one path variable and body and async return" in {
      val jsonRequest = ByteString(s"""
           |{
           |  "value": "Joe"
           |}
        """.stripMargin)

      // create a POST request
      val request = HttpRequest(
        HttpMethods.POST,
        uri = "/hello/20/async",
        entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

      request ~> Route.seal(route) ~> check {
        responseAs[String] shouldEqual "async => name: Joe, age: 20"
      }
    }

    "return empty response for void method" in {
      val request = HttpRequest(HttpMethods.DELETE, uri = "/hello")

      request ~> Route.seal(route) ~> check {
        response.entity shouldEqual HttpEntity.Empty
      }
    }

    "return empty response for null return" in {
      val request = HttpRequest(HttpMethods.DELETE, uri = "/hello/name")

      request ~> Route.seal(route) ~> check {
        response.entity shouldEqual HttpEntity.Empty
      }
    }

  }
}
