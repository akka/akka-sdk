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
  }

  class SimpleToolImpl extends SimpleTool {
    def echo(message: String): String = message
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
      tools.length shouldBe 1
      tools.head.name shouldBe "echo"
      tools.head.description shouldBe "Simple echo function"
    }

    "create invokers for simple tool" in {

      val invokers = FunctionTools.toolInvokersFor(new SimpleToolImpl())
      invokers.size shouldBe 1
      invokers.contains("echo") shouldBe true

      val echoInvoker = invokers("echo")
      echoInvoker.paramNames shouldBe Array("message")
      echoInvoker.returnType shouldBe classOf[String]

      val result = echoInvoker.invoke(Array("Hello world"))
      result shouldBe "Hello world"
    }

    "find all tools in a class with multiple methods" in {
      val tools = FunctionTools.descriptorsFor(classOf[MultipleMethodsTool])
      tools.length shouldBe 2

      val toolNames = tools.map(_.name).toSet
      (toolNames should contain).allOf("add", "multiply")

      val addTool = tools.find(_.name == "add").get
      addTool.description shouldBe "Adds two numbers"

      val multiplyTool = tools.find(_.name == "multiply").get
      multiplyTool.description shouldBe "Multiplies two numbers"
    }

    "create invokers for all its methods" in {

      val invokers = FunctionTools.toolInvokersFor(new MultipleMethodsTool())
      invokers.size shouldBe 2
      invokers.contains("add") shouldBe true
      invokers.contains("multiply") shouldBe true

      val addInvoker = invokers("add")
      val addResult = addInvoker.invoke(Array(5, 3))
      addResult shouldBe 8

      val multiplyInvoker = invokers("multiply")
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
      val tools = FunctionTools.descriptorsForAgent(classOf[SecretAgent])
      tools.length shouldBe 1
      tools.head.name shouldBe "secretOperation"
      tools.head.description shouldBe "Secret agent operation"
    }

    "access private methods when using agent tool discovery" in {
      val instance = new SecretAgent()
      val invokers = FunctionTools.toolInvokersForAgent(instance)

      invokers.size shouldBe 1
      invokers.contains("secretOperation") shouldBe true

      val secretInvoker = invokers("secretOperation")
      val result = secretInvoker.invoke(Array("classified"))

      result shouldBe "Secret: classified"
    }

    "support dependency injection for tool creation" in {

      val dependencyProvider = new TestDependencyProvider()
      val invokers = FunctionTools.toolInvokersFor(classOf[SimpleToolImpl], Some(dependencyProvider))

      invokers.size shouldBe 1
      invokers.contains("echo") shouldBe true

      val prefixInvoker = invokers("echo")
      val result = prefixInvoker.invoke(Array("Hello"))

      result shouldBe "Hello"
    }

    "fail when no dependency provider is available" in {
      val exception = intercept[IllegalArgumentException] {
        val invoker = FunctionTools.toolInvokersFor(classOf[SimpleToolImpl], None)
        invoker("echo").invoke(Array("Hello"))
      }
      exception.getMessage should include("no DependencyProvider was configured")
    }
  }
}
