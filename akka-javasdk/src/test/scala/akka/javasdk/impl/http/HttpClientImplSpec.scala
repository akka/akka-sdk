/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.javadsl.model.HttpHeader
import akka.javasdk.JsonSupport
import akka.javasdk.http.HttpResponses
import akka.javasdk.http.RequestBuilder
import akka.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HttpClientImplSpec extends AnyWordSpec with Matchers {
  val system: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty[Nothing], "httpClient")

  "RequestBuilderImpl" should {
    "add query parameter" in new HttpClientImplSuite {
      val builder: RequestBuilderImpl[ByteString] = get { builder =>
        builder
          .addQueryParameter("key", "some value")
          .addQueryParameter("another", "name")
      }
      builder.request.getUri.toString shouldBe "http://test.com/test?key=some+value&another=name"
    }

    "get a String as raw string" in new HttpClientImplSuite {

      val builder: RequestBuilderImpl[ByteString] = get(identity)
      val res =
        builder
          .bodyParserInternal(classOf[String], HttpResponses.ok("foo"), ByteString.fromString("foo"))
          .body()

      res shouldBe "foo"
    }

    "get a json payload as raw string" in new HttpClientImplSuite {

      val builder: RequestBuilderImpl[ByteString] = get(identity)
      case class Foo(value: String)
      val foobar = Foo("bar")
      val jsonPayload = JsonSupport.encodeToAkkaByteString(foobar)

      val res =
        builder
          .bodyParserInternal(classOf[String], HttpResponses.ok(foobar), jsonPayload)
          .body()

      res shouldBe jsonPayload.utf8String
    }

    "get an integer as raw string" in new HttpClientImplSuite {

      val builder: RequestBuilderImpl[ByteString] = get(identity)
      val jsonPayload = JsonSupport.encodeToAkkaByteString(10)

      val res =
        builder
          .bodyParserInternal(classOf[String], HttpResponses.ok(10), jsonPayload)
          .body()

      res shouldBe "10"
    }

    "get a boolean as raw string" in new HttpClientImplSuite {

      val builder: RequestBuilderImpl[ByteString] = get(identity)
      val jsonPayload = JsonSupport.encodeToAkkaByteString(true)

      val res =
        builder
          .bodyParserInternal(classOf[String], HttpResponses.ok(true), jsonPayload)
          .body()

      res shouldBe "true"
    }
  }

}

trait HttpClientImplSuite {
  implicit private val system: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty[Nothing], "RequestBuilderImplSpec")

  val baseUrl = "http://test.com"
  val path = "/test"
  val headers: Seq[HttpHeader] = Seq.empty

  private def client = new HttpClientImpl(system, baseUrl, headers)

  private def get(url: String)(
      builder: RequestBuilder[ByteString] => RequestBuilder[ByteString]): RequestBuilderImpl[ByteString] = {
    builder(client.GET(url))
      .asInstanceOf[RequestBuilderImpl[ByteString]]
  }

  def get(builder: RequestBuilder[ByteString] => RequestBuilder[ByteString]): RequestBuilderImpl[ByteString] =
    get(path)(builder)
}
