/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.javasdk.DependencyProvider
import akka.javasdk.agent.Agent
import akka.javasdk.annotations.FunctionTool
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FunctionToolsSpec extends AnyWordSpec with Matchers {

  trait SimpleTool {
    @FunctionTool(description = "Simple echo function")
    def echo(message: String): String

    @FunctionTool(description = "Simple echo function with number")
    def echo(num: Int): String
  }

  class SimpleToolImpl extends SimpleTool {
    override def echo(message: String): String = message

    override def echo(num: Int): String = num.toString
  }

  class MultipleMethodsTool {
    @FunctionTool(description = "Adds two numbers")
    def add(a: Int, b: Int): Int = a + b

    @FunctionTool(description = "Multiplies two numbers")
    def multiply(a: Int, b: Int): Int = a * b

    def notATool(a: Int): Int = a // Not annotated
  }

  class CustomNameTool {
    @FunctionTool(name = "customGreet", description = "Greeting with custom name")
    def greet(name: String): String = s"Hello, $name!"
  }

  class PrivateMethodTool {
    @FunctionTool(description = "Private method tool")
    private def secretOperation(input: String): String = s"Secret: $input"

    // need this otherwise compiler complains that secretOperation is never used
    def publicMethod(input: String): String = secretOperation(input)
  }

  class SecretAgent extends Agent {
    @FunctionTool(description = "Secret agent operation")
    private def secretOperation(input: String): String = s"Secret: $input"

    def publicMethod(data: String): String = secretOperation(data)

  }
  // Custom dependency provider for testing
  class TestDependencyProvider extends DependencyProvider {
    override def getDependency[T](cls: Class[T]): T = {
      if (cls == classOf[SimpleToolImpl])
        new SimpleToolImpl().asInstanceOf[T]
      else
        throw new IllegalArgumentException(s"Unknown dependency: ${cls.getName}")
    }
  }

  "FunctionTools" should {

    "find tools in a simple class" in {
      val tools = FunctionTools.descriptorsFor(classOf[SimpleToolImpl])
      tools.length shouldBe 2
      tools.map(_.name) should contain allOf (
        "SimpleToolImpl_echo_String",
        "SimpleToolImpl_echo_int"
      )
    }

    "create invokers for simple tool" in {

      val invokers = FunctionTools.toolInvokersFor(new SimpleToolImpl())
      invokers.size shouldBe 2

      invokers.contains("SimpleToolImpl_echo_String") shouldBe true
      invokers.contains("SimpleToolImpl_echo_int") shouldBe true

      { // string invoker
        val echoInvoker = invokers("SimpleToolImpl_echo_String")
        echoInvoker.paramNames shouldBe Array("message")
        echoInvoker.returnType shouldBe classOf[String]

        val result = echoInvoker.invoke(Array("Hello world"))
        result shouldBe "Hello world"
      }

      { // number invoker
        val echoInvoker = invokers("SimpleToolImpl_echo_int")
        echoInvoker.paramNames shouldBe Array("num")
        echoInvoker.returnType shouldBe classOf[String]

        val result = echoInvoker.invoke(Array(10))
        result shouldBe "10"
      }
    }

    "find all tools in a class with multiple methods" in {
      val tools = FunctionTools.descriptorsFor(classOf[MultipleMethodsTool])
      tools.length shouldBe 2

      val toolNames = tools.map(_.name).toSet
      (toolNames should contain).allOf("MultipleMethodsTool_add", "MultipleMethodsTool_multiply")

      val addTool = tools.find(_.name == "MultipleMethodsTool_add").get
      addTool.description shouldBe "Adds two numbers"

      val multiplyTool = tools.find(_.name == "MultipleMethodsTool_multiply").get
      multiplyTool.description shouldBe "Multiplies two numbers"
    }

    "create invokers for all its methods" in {

      val invokers = FunctionTools.toolInvokersFor(new MultipleMethodsTool())
      invokers.size shouldBe 2
      invokers.contains("MultipleMethodsTool_add") shouldBe true
      invokers.contains("MultipleMethodsTool_multiply") shouldBe true

      val addInvoker = invokers("MultipleMethodsTool_add")
      val addResult = addInvoker.invoke(Array(5, 3))
      addResult shouldBe 8

      val multiplyInvoker = invokers("MultipleMethodsTool_multiply")
      val multiplyResult = multiplyInvoker.invoke(Array(5, 3))
      multiplyResult shouldBe 15
    }

    "respect custom tool names" in {
      val tools = FunctionTools.descriptorsFor(classOf[CustomNameTool])
      tools.length shouldBe 1
      tools.head.name shouldBe "customGreet"
      tools.head.description shouldBe "Greeting with custom name"

      val invoker = FunctionTools.toolInvokersFor(new CustomNameTool())("customGreet")
      invoker.invoke(Array("Alice")) shouldBe "Hello, Alice!"
    }

    "throw exception when no tools found" in {
      class NoToolsClass {
        def method(): Unit = {}
      }

      val exception = intercept[IllegalArgumentException] {
        FunctionTools.descriptorsFor(classOf[NoToolsClass])
      }

      exception.getMessage should include("No tools found in class")
    }

    "not expose private methods in normal tool discovery" in {

      val exception = intercept[IllegalArgumentException] {
        FunctionTools.descriptorsFor(classOf[PrivateMethodTool])
      }

      exception.getMessage should include(s"No tools found in class [${classOf[PrivateMethodTool].getName}]")
    }

    "generate tool descriptors for private methods when using agent tool discovery" in {
      val tools = FunctionTools.descriptorsFor(classOf[SecretAgent])
      tools.length shouldBe 1
      tools.head.name shouldBe "SecretAgent_secretOperation"
      tools.head.description shouldBe "Secret agent operation"
    }

    "access private methods when using agent tool discovery" in {
      val instance = new SecretAgent()
      val invokers = FunctionTools.toolInvokersFor(instance)

      invokers.size shouldBe 1
      invokers.contains("SecretAgent_secretOperation") shouldBe true

      val secretInvoker = invokers("SecretAgent_secretOperation")
      val result = secretInvoker.invoke(Array("classified"))

      result shouldBe "Secret: classified"
    }

    "support dependency injection for tool creation" in {

      val dependencyProvider = new TestDependencyProvider()
      val invokers = FunctionTools.toolInvokersFor(classOf[SimpleToolImpl], Some(dependencyProvider))

      invokers.size shouldBe 2
      invokers.contains("SimpleToolImpl_echo_String") shouldBe true

      val prefixInvoker = invokers("SimpleToolImpl_echo_String")
      val result = prefixInvoker.invoke(Array("Hello"))

      result shouldBe "Hello"
    }

    "fail when no dependency provider is available" in {
      val exception = intercept[IllegalArgumentException] {
        val invoker = FunctionTools.toolInvokersFor(classOf[SimpleToolImpl], None)
        invoker("SimpleToolImpl_echo_String").invoke(Array("Hello"))
      }
      exception.getMessage should include("no DependencyProvider was configured")
    }
  }
}
