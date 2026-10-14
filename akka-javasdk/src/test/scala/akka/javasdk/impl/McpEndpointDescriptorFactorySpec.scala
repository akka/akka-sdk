/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.concurrent.ExecutionContext

import akka.javasdk.impl.mcp.TestMcpEndpoints
import akka.runtime.sdk.spi.McpEndpointDescriptor.TextContent
import akka.runtime.sdk.spi.McpEndpointDescriptor.ToolCallResult
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaArray
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaObject
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class McpEndpointDescriptorFactorySpec extends AnyWordSpec with Matchers with ScalaFutures {

  private implicit val ec: ExecutionContext = ExecutionContext.parasitic

  "The MCP endpoint descriptor factory" should {

    "pick up tools, prompts and resources inherited from a base class" in {
      val descriptor =
        McpEndpointDescriptorFactory(classOf[TestMcpEndpoints.InheritingMcpEndpoint], _ => null)

      descriptor.tools.map(_.toolDescription.name).toSet should ===(
        Set("inherited-tool", "own-tool", "overridden-tool"))
      descriptor.prompts.map(_.prompt.name).toSet should ===(Set("inherited-prompt"))
      descriptor.resources.map(_.resource.name).toSet should ===(Set("inherited-resource"))

      // the overriding declaration keeps the tool, with the override description
      val overridden = descriptor.tools.find(_.toolDescription.name == "overridden-tool").get
      overridden.toolDescription.description should ===("override")

      // override without annotation opts the inherited tool out
      descriptor.tools.map(_.toolDescription.name) should not contain "dropped-tool"
    }

    "classify tool return types" in {
      val tools = structuredDescriptor.tools.map(t => t.toolDescription.name -> t.toolDescription).toMap

      val weatherSchema = tools("null-weather").outputSchema.get
      weatherSchema.properties.keySet should ===(Set("city", "tempC"))
      tools("async-weather").outputSchema.get.properties.keySet should ===(Set("city", "tempC"))
      tools("map").outputSchema.get.properties shouldBe empty

      for (arrayTool <- Seq("weather-list", "weather-array")) {
        val schema = tools(arrayTool).outputSchema.get
        schema.required should ===(Seq("result"))
        val items = schema.properties("result").asInstanceOf[JsonSchemaArray].items.asInstanceOf[JsonSchemaObject]
        items.properties.keySet should ===(Set("city", "tempC"))
      }

      for (textTool <- Seq("null-text", "void-tool", "async-void-tool", "optional-weather", "unit"))
        withClue(textTool) { tools(textTool).outputSchema shouldBe empty }
    }

    "return structured, wrapped, and text tool results" in {
      val listResult = call("weather-list")
      listResult.content shouldBe empty
      val list = listResult.structuredContent.get("result").asInstanceOf[java.util.List[_]]
      list.size() should ===(2)

      call("weather-array").structuredContent.get("result").asInstanceOf[java.util.List[_]].size() should ===(1)
      call("async-weather").structuredContent.get should ===(Map("city" -> "Leuven", "tempC" -> 20))
      call("map").structuredContent.get should ===(Map("Leuven" -> 20))

      textOf(call("void-tool")) should ===("SUCCESS")
      textOf(call("async-void-tool")) should ===("SUCCESS")
      textOf(call("unit")) should ===("\"CELSIUS\"")
      textOf(call("optional-weather")) should ===("""{"city":"Leuven","tempC":20}""")
    }

    "fail tool calls returning null" in {
      for (tool <- Seq("null-weather", "null-text", "null-async")) withClue(tool) {
        val exception = structuredDescriptor.tools
          .find(_.toolDescription.name == tool)
          .get
          .call(null, Map.empty)
          .failed
          .futureValue
        exception shouldBe an[IllegalStateException]
        exception.getMessage should include("returned null")
      }
    }

    "unwrap the failure of an async tool" in {
      val exception = structuredDescriptor.tools
        .find(_.toolDescription.name == "async-fail")
        .get
        .call(null, Map.empty)
        .failed
        .futureValue
      exception shouldBe an[IllegalStateException]
      exception.getMessage should ===("failed: async")
    }

    "leave empty optional fields out of structured content" in {
      val schema = structuredDescriptor.tools.find(_.toolDescription.name == "note-without-extra").get.toolDescription
      schema.outputSchema.get.properties.keySet should ===(Set("title", "extra"))
      schema.outputSchema.get.required should ===(Seq("title"))
      call("note-without-extra").structuredContent.get should ===(Map("title" -> "t"))
    }

    "reject unsupported tool return types" in {
      intercept[ValidationException] {
        McpEndpointDescriptorFactory(classOf[TestMcpEndpoints.RawCompletionStageMcpEndpoint], _ => null)
      }.getMessage should include("CompletionStage with a type parameter")
      for (protobufEndpoint <- Seq(
          classOf[TestMcpEndpoints.ProtobufMcpEndpoint],
          classOf[TestMcpEndpoints.ProtobufListMcpEndpoint],
          classOf[TestMcpEndpoints.ProtobufFieldMcpEndpoint]))
        withClue(protobufEndpoint.getSimpleName) {
          intercept[ValidationException] {
            McpEndpointDescriptorFactory(protobufEndpoint, _ => null)
          }.getMessage should include("protobuf message")
        }
      intercept[ValidationException] {
        McpEndpointDescriptorFactory(classOf[TestMcpEndpoints.RawCollectionMcpEndpoint], _ => null)
      }.getMessage should include("parameterized java.util.Collection")
    }
  }

  private lazy val structuredDescriptor =
    McpEndpointDescriptorFactory(
      classOf[TestMcpEndpoints.StructuredOutputMcpEndpoint],
      _ => new TestMcpEndpoints.StructuredOutputMcpEndpoint)

  private def call(toolName: String): ToolCallResult =
    // the test endpoint factory ignores the construction context
    structuredDescriptor.tools.find(_.toolDescription.name == toolName).get.call(null, Map.empty).futureValue

  private def textOf(result: ToolCallResult): String = {
    result.structuredContent shouldBe empty
    result.content.collect { case text: TextContent => text.text } match {
      case Seq(text) => text
      case other     => fail(s"Expected single text content, got $other")
    }
  }
}
