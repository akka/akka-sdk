/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.Done
import akka.javasdk.DependencyProvider
import akka.javasdk.agent.Agent
import akka.javasdk.annotations.FunctionTool
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.JsonSchema.emptyObjectSchema
import akka.javasdk.impl.agent.FunctionToolsSpec.CustomNameTool
import akka.javasdk.impl.agent.FunctionToolsSpec.ESEntityAsTool
import akka.javasdk.impl.agent.FunctionToolsSpec.KVEntityAsTool
import akka.javasdk.impl.agent.FunctionToolsSpec.MultipleMethodsTool
import akka.javasdk.impl.agent.FunctionToolsSpec.PrivateMethodTool
import akka.javasdk.impl.agent.FunctionToolsSpec.SecretAgent
import akka.javasdk.impl.agent.FunctionToolsSpec.SimpleToolImpl
import akka.javasdk.impl.agent.FunctionToolsSpec.SimpleToolWrapper
import akka.javasdk.impl.agent.FunctionToolsSpec.TestDependencyProvider
import akka.javasdk.impl.agent.FunctionToolsSpec.ViewAsTool
import akka.javasdk.impl.agent.FunctionToolsSpec.WorkflowAsTool
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.view.View
import akka.javasdk.workflow.Workflow
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object FunctionToolsSpec {

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

  object SimpleToolWrapper {
    class SimpleToolImpl extends SimpleTool {
      override def echo(message: String): String = message

      override def echo(num: Int): String = num.toString
    }
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

  class ESEntityAsTool extends EventSourcedEntity[String, String] {
    @FunctionTool(description = "a method without arg")
    def method(): EventSourcedEntity.Effect[Done] = effects().reply(Done)

    @FunctionTool(description = "a method with 1 String arg")
    def method(inputString: String): EventSourcedEntity.Effect[Done] = effects().reply(Done)

    @FunctionTool(description = "a method with 1 Int arg")
    def method(inputInt: Int): EventSourcedEntity.Effect[Done] = effects().reply(Done)

    override def applyEvent(event: String): String = ???
  }

  class KVEntityAsTool extends KeyValueEntity[String] {

    @FunctionTool(description = "a method without arg")
    def method(): KeyValueEntity.Effect[Done] = effects().reply(Done)

    @FunctionTool(description = "a method with 1 String arg")
    def method(inputString: String): KeyValueEntity.Effect[Done] = effects().reply(Done)

    @FunctionTool(description = "a method with 1 Int arg")
    def method(inputInt: Int): KeyValueEntity.Effect[Done] = effects().reply(Done)

  }

  class WorkflowAsTool extends Workflow[String] {

    @FunctionTool(description = "a method without arg")
    def method(): Workflow.Effect[Done] = effects().reply(Done)

    @FunctionTool(description = "a method with 1 String arg")
    def method(inputString: String): Workflow.Effect[Done] = effects().reply(Done)

    @FunctionTool(description = "a method with 1 Int arg")
    def method(inputInt: Int): Workflow.Effect[Done] = effects().reply(Done)

  }

  class ViewAsTool extends View {

    @FunctionTool(description = "a method without arg")
    def method(): View.QueryEffect[String] = queryResult()

    @FunctionTool(description = "a method with 1 String arg")
    def method(inputString: String): View.QueryEffect[String] = queryResult()

  }
}
class FunctionToolsSpec extends AnyWordSpec with Matchers {

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

    "adding two tools with the same name should fail" in {

      val exception = intercept[IllegalArgumentException] {
        FunctionTools.validateNames(Seq(classOf[SimpleToolImpl], classOf[SimpleToolWrapper.SimpleToolImpl]))
      }

      exception.getMessage should include("Duplicate tool names found:")
    }

    "throw exception when no tools found in an EventSourcedEntity class" in {
      class NoToolsEntity extends EventSourcedEntity[Int, String] {
        def method(): EventSourcedEntity.Effect[Done] = effects().reply(Done)
        override def applyEvent(event: String): Int = ???
      }

      val exception = intercept[IllegalArgumentException] {
        FunctionTools.descriptorsFor(classOf[NoToolsEntity])
      }

      exception.getMessage should include("No tools found in class")
    }

    "throw exception when no tools found in a KeyValueEntity class" in {
      class NoToolsEntity extends KeyValueEntity[String] {
        def method(): KeyValueEntity.Effect[Done] = effects().reply(Done)
      }

      val exception = intercept[IllegalArgumentException] {
        FunctionTools.descriptorsFor(classOf[NoToolsEntity])
      }

      exception.getMessage should include("No tools found in class")
    }

    "throw exception when no tools found in an Workflow class" in {
      class NoToolsWorkflow extends Workflow[String] {
        def method(): Workflow.Effect[Done] = effects().reply(Done)
      }

      val exception = intercept[IllegalArgumentException] {
        FunctionTools.descriptorsFor(classOf[NoToolsWorkflow])
      }

      exception.getMessage should include("No tools found in class")
    }

    "throw exception when no tools found in a View class" in {
      class NoToolsView extends View {
        def method(): View.QueryEffect[Done] = ???
      }

      val exception = intercept[IllegalArgumentException] {
        FunctionTools.descriptorsFor(classOf[NoToolsView])
      }

      exception.getMessage should include("No tools found in class")
    }

    "find all tools in an EventSourcedEntity class" in {

      val descriptors = FunctionTools.descriptorsFor(classOf[ESEntityAsTool])
      descriptors.size shouldBe 3

      val descMethodVoid = descriptors.find(_.name == "ESEntityAsTool_method").get
      descMethodVoid.description shouldBe "a method without arg"
      descMethodVoid.schema.required.contains("uniqueId") shouldBe true
      descMethodVoid.schema.required.size shouldBe 1

      val descMethodString = descriptors.find(_.name == "ESEntityAsTool_method_String").get
      descMethodString.description shouldBe "a method with 1 String arg"

      descMethodString.schema.required.contains("uniqueId") shouldBe true
      descMethodString.schema.required.contains("inputString") shouldBe true

      val descMethodInt = descriptors.find(_.name == "ESEntityAsTool_method_int").get
      descMethodInt.description shouldBe "a method with 1 Int arg"

      descMethodInt.schema.required.contains("uniqueId") shouldBe true
      descMethodInt.schema.required.contains("inputInt") shouldBe true

    }

    "find all tools in an KeyValueEntity class" in {

      val descriptors = FunctionTools.descriptorsFor(classOf[KVEntityAsTool])
      descriptors.size shouldBe 3

      val descMethodVoid = descriptors.find(_.name == "KVEntityAsTool_method").get
      descMethodVoid.description shouldBe "a method without arg"
      descMethodVoid.schema.required.contains("uniqueId") shouldBe true
      descMethodVoid.schema.required.size shouldBe 1

      val descMethodString = descriptors.find(_.name == "KVEntityAsTool_method_String").get
      descMethodString.description shouldBe "a method with 1 String arg"

      descMethodString.schema.required.contains("uniqueId") shouldBe true
      descMethodString.schema.required.contains("inputString") shouldBe true

      val descMethodInt = descriptors.find(_.name == "KVEntityAsTool_method_int").get
      descMethodInt.description shouldBe "a method with 1 Int arg"

      descMethodInt.schema.required.contains("uniqueId") shouldBe true
      descMethodInt.schema.required.contains("inputInt") shouldBe true

    }

    "find all tools in an Workflow class" in {

      val descriptors = FunctionTools.descriptorsFor(classOf[WorkflowAsTool])
      descriptors.size shouldBe 3

      val descMethodVoid = descriptors.find(_.name == "WorkflowAsTool_method").get
      descMethodVoid.description shouldBe "a method without arg"

      descMethodVoid.schema.required.contains("uniqueId") shouldBe true
      descMethodVoid.schema.required.size shouldBe 1

      val descMethodString = descriptors.find(_.name == "WorkflowAsTool_method_String").get
      descMethodString.description shouldBe "a method with 1 String arg"

      descMethodString.schema.required.contains("uniqueId") shouldBe true
      descMethodString.schema.required.contains("inputString") shouldBe true

      val descMethodInt = descriptors.find(_.name == "WorkflowAsTool_method_int").get
      descMethodInt.description shouldBe "a method with 1 Int arg"

      descMethodInt.schema.required.contains("uniqueId") shouldBe true
      descMethodInt.schema.required.contains("inputInt") shouldBe true

    }

    "find all tools in a View class" in {

      val descriptors = FunctionTools.descriptorsFor(classOf[ViewAsTool])
      descriptors.size shouldBe 2

      val descMethodVoid = descriptors.find(_.name == "ViewAsTool_method").get
      descMethodVoid.description shouldBe "a method without arg"
      descMethodVoid.schema shouldBe emptyObjectSchema

      val descMethodString = descriptors.find(_.name == "ViewAsTool_method_String").get
      descMethodString.description shouldBe "a method with 1 String arg"

      descMethodString.schema.required.contains("inputString") shouldBe true

    }
  }
}
