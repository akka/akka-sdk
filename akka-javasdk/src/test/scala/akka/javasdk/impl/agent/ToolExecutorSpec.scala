/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.lang.reflect.Type
import java.util.Optional

import scala.jdk.CollectionConverters._

import akka.javasdk.JsonSupport
import akka.javasdk.impl.agent.FunctionTools.FunctionToolInvoker
import akka.javasdk.impl.agent.ToolExecutorSpec.Bar
import akka.javasdk.impl.agent.ToolExecutorSpec.Foo
import akka.javasdk.impl.agent.ToolExecutorSpec.Foobar
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiMetadata
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

  private val serializer = new JsonSerializer(JsonSupport.getObjectMapper)

  private def functionToolFor(any: Any): FunctionToolInvoker = {
    val method = any.getClass.getMethods.collectFirst {
      case m if m.getName == "method" => m
    }.get

    new FunctionToolInvoker {
      override def paramNames: Array[String] = method.getParameters.map(_.getName)
      override def types: Array[Type] = method.getGenericParameterTypes
      override def invoke(args: Array[Any]): Any = method.invoke(any, args: _*)
      override def returnType: Class[_] = method.getReturnType
    }
  }

  private def toolRequest(name: String, args: String): SpiAgent.ToolCallCommand =
    new SpiAgent.ToolCallCommand("001", name, args, SpiMetadata.empty, io.opentelemetry.context.Context.root())

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
  }
}
