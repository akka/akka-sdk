/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.lang.reflect.Type
import java.util.Optional

import scala.jdk.CollectionConverters._

import akka.javasdk.JsonSupport
import akka.javasdk.agent.MessageContent
import akka.javasdk.impl.agent.FunctionTools.FunctionToolInvoker
import akka.javasdk.impl.agent.ToolExecutorSpec.Bar
import akka.javasdk.impl.agent.ToolExecutorSpec.Foo
import akka.javasdk.impl.agent.ToolExecutorSpec.Foobar
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiMetadata
import io.opentelemetry.context.{ Context => TelemetryContext }
import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object ToolExecutorSpec {
  // Test data classes for complex type tests
  case class Foo(name: String)
  case class Bar(name: String, foo: Foo)
  case class Foobar(foo: Foo, bar: Bar)
}

class ToolExecutorSpec extends AnyWordSpecLike with TestSuite with Matchers {

  private val serializer = new Serializer(JsonSupport.getObjectMapper)

  private def functionToolFor(any: Any): FunctionToolInvoker = {
    val method = any.getClass.getMethods.collectFirst {
      case m if m.getName == "method" => m
    }.get

    new FunctionToolInvoker {
      override def paramNames: Array[String] = method.getParameters.map(_.getName)
      override def types: Array[Type] = method.getGenericParameterTypes
      override def invoke(args: Array[Any]): Any = method.invoke(any, args: _*)
      override def returnType: Class[_] = method.getReturnType
      override def genericReturnType: Type = method.getGenericReturnType
    }
  }

  private def toolRequest(name: String, args: String): SpiAgent.ToolCallCommand =
    new SpiAgent.ToolCallCommand("001", name, args, SpiMetadata.empty, TelemetryContext.root)

  "The ToolExecutor" should {

    "call a tool function returning a string" in {
      class TestClass {
        def method(value: String): String = value
      }
      val functionTool = functionToolFor(new TestClass)
      val executor = new ToolExecutor(Map("method" -> functionTool), serializer)

      val req = toolRequest("method", """{ "value": "bar" }""")
      val result = executor.execute(req)
      result shouldBe "bar"
    }

    "call a tool function returning void" in {
      class TestClass {
        def method(value: String): Unit = ()
      }
      val functionTool = functionToolFor(new TestClass)
      val executor = new ToolExecutor(Map("method" -> functionTool), serializer)

      val req = toolRequest("method", """{ "value": "bar" }""")
      val result = executor.execute(req)
      result shouldBe "SUCCESS"
    }

    "call a tool function returning primitive boolean" in {
      class TestClass {
        def method(value: String): Boolean = true
      }
      val functionTool = functionToolFor(new TestClass)
      val executor = new ToolExecutor(Map("method" -> functionTool), serializer)

      val req = toolRequest("method", """{ "value": "bar" }""")
      val result = executor.execute(req)
      result shouldBe "true"
    }

    "call a tool function returning primitive Int" in {
      class TestClass {
        def method(value: String): Int = 10
      }
      val functionTool = functionToolFor(new TestClass)
      val executor = new ToolExecutor(Map("method" -> functionTool), serializer)

      val req = toolRequest("method", """{ "value": "bar" }""")
      val result = executor.execute(req)
      result shouldBe "10"
    }

    "call a tool function returning primitive Long" in {
      class TestClass {
        def method(value: String): Long = 10
      }
      val functionTool = functionToolFor(new TestClass)
      val executor = new ToolExecutor(Map("method" -> functionTool), serializer)

      val req = toolRequest("method", """{ "value": "bar" }""")
      val result = executor.execute(req)
      result shouldBe "10"
    }

    "call a tool function returning primitive Double" in {
      class TestClass {
        def method(value: String): Double = 10.2
      }
      val functionTool = functionToolFor(new TestClass)
      val executor = new ToolExecutor(Map("method" -> functionTool), serializer)

      val req = toolRequest("method", """{ "value": "bar" }""")
      val result = executor.execute(req)
      result shouldBe "10.2"
    }

    "call a tool function returning a complex type " in {
      class TestClass {
        def method(value: String): Foo = Foo(value)
      }
      val functionTool = functionToolFor(new TestClass)
      val executor = new ToolExecutor(Map("method" -> functionTool), serializer)

      val req = toolRequest("method", """{ "value": "bar" }""")
      val result = executor.execute(req)

      val resultAsJsonNode = serializer.objectMapper.readTree(result)
      val expectedJsonNode = serializer.objectMapper.readTree("""{"name":"bar"}""")

      resultAsJsonNode shouldBe expectedJsonNode
    }

    "call a tool function receiving a complex type (Foo)" in {
      class TestClass {
        def method(foo: Foo): String = foo.name
      }
      val functionTool = functionToolFor(new TestClass)
      val executor = new ToolExecutor(Map("method" -> functionTool), serializer)

      val req = toolRequest("method", """{ "foo": { "name": "bar" } }""")
      val result = executor.execute(req)

      result shouldBe "bar"
    }

    "call a tool function receiving a complex type (Foo) and an empty Optional" in {
      class TestClass {
        def method(foo: Foo, str: Optional[String]): String = {
          if (str.isPresent) throw new IllegalArgumentException("str should not be present")
          else
            s"${foo.name} and Optional.empty"
        }
      }
      val functionTool = functionToolFor(new TestClass)
      val executor = new ToolExecutor(Map("method" -> functionTool), serializer)

      val req = toolRequest("method", """{ "foo": { "name": "bar" } }""")
      val result = executor.execute(req)

      result shouldBe "bar and Optional.empty"
    }

    "call a tool function receiving a complex type (Foo) and an explicit null value" in {
      class TestClass {
        def method(foo: Foo, str: Optional[String]): String = {
          if (str.isPresent) throw new IllegalArgumentException("str should not be present")
          else
            s"${foo.name} and Optional.empty"
        }
      }
      val functionTool = functionToolFor(new TestClass)
      val executor = new ToolExecutor(Map("method" -> functionTool), serializer)

      val req = toolRequest("method", """{ "foo": { "name": "bar" }, "str": null }""")
      val result = executor.execute(req)

      result shouldBe "bar and Optional.empty"
    }

    "call a tool function receiving a complex type (Foo) and a non-empty Optional" in {
      class TestClass {
        def method(foo: Foo, str: Optional[String]): String = {
          if (str.isPresent)
            s"${foo.name} and ${str.get()}"
          else
            throw new IllegalArgumentException("str should be present")
        }
      }
      val functionTool = functionToolFor(new TestClass)
      val executor = new ToolExecutor(Map("method" -> functionTool), serializer)

      val req = toolRequest("method", """{ "foo": { "name": "bar" }, "str": "string-value" }""")
      val result = executor.execute(req)

      result shouldBe "bar and string-value"
    }

    "call a tool function receiving multiple complex types (Foo, Bar, FooBar)" in {
      class TestClass {
        def method(foo: Foo, bar: Bar, foobar: Foobar): String =
          foo.getClass.getSimpleName + "-" +
          bar.getClass.getSimpleName + "-" +
          foobar.getClass.getSimpleName
      }
      val functionTool = functionToolFor(new TestClass)
      val executor = new ToolExecutor(Map("method" -> functionTool), serializer)

      val req = toolRequest(
        "method",
        """
                                        |{
                                        |  "foo": { "name": "foo" },
                                        |  "bar": { "name": "bar", "foo": { "name": "foo2" }  },
                                        |  "foobar": {
                                        |     "foo": { "name": "foo3" },
                                        |     "bar": {
                                        |       "name": "bar3",
                                        |       "foo": { "name": "foo4" }
                                        |     }
                                        |  }
                                        |}""".stripMargin)

      val result = executor.execute(req)
      result shouldBe "Foo-Bar-Foobar"
    }

    "call a tool function receiving a list of Foos" in {
      class TestClass {
        def method(fooList: java.util.List[Foo]): String = {
          fooList.asScala.map(_.name).mkString("-")
        }
      }
      val functionTool = functionToolFor(new TestClass)
      val executor = new ToolExecutor(Map("method" -> functionTool), serializer)

      val req = toolRequest(
        "method",
        """
                                        |{
                                        |  "fooList": [
                                        |    { "name": "foo1" },
                                        |    { "name": "foo2" },
                                        |    { "name": "foo3" }
                                        |  ]
                                        |}""".stripMargin)

      val result = executor.execute(req)
      result shouldBe "foo1-foo2-foo3"
    }

    "throw exception for unknown tool" in {
      val executor = new ToolExecutor(Map.empty, serializer)
      val req = toolRequest("unknown", "{}")

      intercept[IllegalArgumentException] {
        executor.execute(req)
      }
    }

    "executeMultimodal returns a single image content when a tool returns a MessageContent" in {
      class TestClass {
        def method(value: String): MessageContent =
          MessageContent.ImageMessageContent.fromUri("object://photos/" + value + ".png")
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val result = executor.executeMultimodal(toolRequest("method", """{ "value": "sunset" }"""))

      result match {
        case Seq(img: SpiAgent.ImageUriMessageContent) =>
          img.uri.toString shouldBe "object://photos/sunset.png"
        case other => fail(s"unexpected result: $other")
      }
    }

    "executeMultimodal returns inline image bytes when a tool returns image bytes" in {
      val png = "fake png bytes".getBytes

      class TestClass {
        def method(value: String): MessageContent =
          MessageContent.ImageMessageContent.fromBytes(png, "image/png")
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val result = executor.executeMultimodal(toolRequest("method", """{ "value": "sunset" }"""))

      result match {
        case Seq(img: SpiAgent.ImageBytesMessageContent) =>
          img.bytes.toArrayUnsafe() shouldBe png
          img.mimeType shouldBe "image/png"
        case other => fail(s"unexpected result: $other")
      }
    }

    "executeMultimodal returns inline pdf bytes when a tool returns pdf bytes" in {
      val pdf = "fake pdf bytes".getBytes

      class TestClass {
        def method(value: String): MessageContent =
          MessageContent.PdfMessageContent.fromBytes(pdf)
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val result = executor.executeMultimodal(toolRequest("method", """{ "value": "report" }"""))

      result match {
        case Seq(doc: SpiAgent.PdfBytesMessageContent) =>
          doc.bytes.toArrayUnsafe() shouldBe pdf
        case other => fail(s"unexpected result: $other")
      }
    }

    "executeMultimodal wraps a plain String result as a single text content" in {
      class TestClass {
        def method(value: String): String = value
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val result = executor.executeMultimodal(toolRequest("method", """{ "value": "bar" }"""))

      result match {
        case Seq(txt: SpiAgent.TextMessageContent) => txt.text shouldBe "bar"
        case other                                 => fail(s"unexpected result: $other")
      }
    }

    "executeMultimodal wraps a complex (JSON) result as a single text content" in {
      class TestClass {
        def method(value: String): Foo = Foo(value)
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val result = executor.executeMultimodal(toolRequest("method", """{ "value": "bar" }"""))

      result match {
        case Seq(txt: SpiAgent.TextMessageContent) =>
          serializer.objectMapper.readTree(txt.text) shouldBe serializer.objectMapper.readTree("""{"name":"bar"}""")
        case other => fail(s"unexpected result: $other")
      }
    }

    "executeMultimodal returns every element when a tool returns a List<MessageContent>" in {
      val png = "fake png bytes".getBytes
      class TestClass {
        def method(value: String): java.util.List[MessageContent] =
          java.util.List.of(
            MessageContent.TextMessageContent.from("caption: " + value),
            MessageContent.ImageMessageContent.fromBytes(png, "image/png"))
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val result = executor.executeMultimodal(toolRequest("method", """{ "value": "sunset" }"""))

      result match {
        case Seq(txt: SpiAgent.TextMessageContent, img: SpiAgent.ImageBytesMessageContent) =>
          txt.text shouldBe "caption: sunset"
          img.bytes.toArrayUnsafe() shouldBe png
          img.mimeType shouldBe "image/png"
        case other => fail(s"unexpected result: $other")
      }
    }

    "executeMultimodal treats a List of a MessageContent subtype as a content list" in {
      val png = "fake png bytes".getBytes
      class TestClass {
        def method(value: String): java.util.List[MessageContent.ImageBytesMessageContent] =
          java.util.List.of(MessageContent.ImageMessageContent.fromBytes(png, "image/png"))
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val result = executor.executeMultimodal(toolRequest("method", """{ "value": "x" }"""))

      result match {
        case Seq(img: SpiAgent.ImageBytesMessageContent) =>
          img.bytes.toArrayUnsafe() shouldBe png
        case other => fail(s"unexpected result: $other")
      }
    }

    "executeMultimodal treats a List<? extends MessageContent> as a content list" in {
      val png = "fake png bytes".getBytes
      class TestClass {
        def method(value: String): java.util.List[_ <: MessageContent] =
          java.util.List.of(MessageContent.ImageMessageContent.fromBytes(png, "image/png"))
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val result = executor.executeMultimodal(toolRequest("method", """{ "value": "x" }"""))

      result match {
        case Seq(img: SpiAgent.ImageBytesMessageContent) =>
          img.bytes.toArrayUnsafe() shouldBe png
        case other => fail(s"unexpected result: $other")
      }
    }

    "executeMultimodal preserves order for a text + image + pdf list" in {
      val png = "fake png bytes".getBytes
      val pdf = "fake pdf bytes".getBytes
      class TestClass {
        def method(value: String): java.util.List[MessageContent] =
          java.util.List.of(
            MessageContent.TextMessageContent.from(value),
            MessageContent.ImageMessageContent.fromBytes(png, "image/png"),
            MessageContent.PdfMessageContent.fromBytes(pdf))
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val result = executor.executeMultimodal(toolRequest("method", """{ "value": "hi" }"""))

      result match {
        case Seq(
              t: SpiAgent.TextMessageContent,
              i: SpiAgent.ImageBytesMessageContent,
              p: SpiAgent.PdfBytesMessageContent) =>
          t.text shouldBe "hi"
          i.bytes.toArrayUnsafe() shouldBe png
          p.bytes.toArrayUnsafe() shouldBe pdf
        case other => fail(s"unexpected result: $other")
      }
    }

    "executeMultimodal filters out null elements from a List<MessageContent>" in {
      class TestClass {
        def method(value: String): java.util.List[MessageContent] =
          java.util.Arrays.asList(
            MessageContent.TextMessageContent.from("a"),
            null,
            MessageContent.TextMessageContent.from("b"))
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val result = executor.executeMultimodal(toolRequest("method", """{ "value": "x" }"""))

      result match {
        case Seq(a: SpiAgent.TextMessageContent, b: SpiAgent.TextMessageContent) =>
          a.text shouldBe "a"
          b.text shouldBe "b"
        case other => fail(s"unexpected result: $other")
      }
    }

    "executeMultimodal throws for an empty List<MessageContent>" in {
      class TestClass {
        def method(value: String): java.util.List[MessageContent] =
          java.util.Collections.emptyList()
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val ex = intercept[IllegalArgumentException] {
        executor.executeMultimodal(toolRequest("method", """{ "value": "x" }"""))
      }
      ex.getMessage should include("returned no content")
    }

    "executeMultimodal throws for a null List<MessageContent>" in {
      class TestClass {
        def method(value: String): java.util.List[MessageContent] = null
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val ex = intercept[IllegalArgumentException] {
        executor.executeMultimodal(toolRequest("method", """{ "value": "x" }"""))
      }
      ex.getMessage should include("returned null")
    }

    "executeMultimodal throws for a List<MessageContent> containing only nulls" in {
      class TestClass {
        def method(value: String): java.util.List[MessageContent] =
          java.util.Arrays.asList(null, null)
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val ex = intercept[IllegalArgumentException] {
        executor.executeMultimodal(toolRequest("method", """{ "value": "x" }"""))
      }
      ex.getMessage should include("returned no content")
    }

    "executeMultimodal does not treat a List of a non-MessageContent type as a content list" in {
      class TestClass {
        def method(value: String): java.util.List[Foo] =
          java.util.List.of(Foo(value))
      }
      val executor = new ToolExecutor(Map("method" -> functionToolFor(new TestClass)), serializer)

      val result = executor.executeMultimodal(toolRequest("method", """{ "value": "bar" }"""))

      result match {
        case Seq(txt: SpiAgent.TextMessageContent) =>
          serializer.objectMapper.readTree(txt.text) shouldBe
            serializer.objectMapper.readTree("""[{"name":"bar"}]""")
        case other => fail(s"unexpected result: $other")
      }
    }
  }
}
