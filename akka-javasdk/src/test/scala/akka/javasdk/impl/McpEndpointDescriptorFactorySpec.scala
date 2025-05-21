/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class McpEndpointDescriptorFactorySpec extends AnyWordSpec with Matchers {

  "The McpEndpointDescriptorFactory" should {

    "extract tool input schema" in {
      val result = McpEndpointDescriptorFactory.inputSchemaFor(classOf[SomeToolInput])

      result.`type` shouldEqual "object"

      result.properties shouldEqual Map(
        "string" -> Mcp.ToolProperty(`type` = "string", description = "some description"),
        "optionalString" -> Mcp.ToolProperty(`type` = "string", description = ""),
        "booleanPrimitive" -> Mcp.ToolProperty(`type` = "boolean", description = ""),
        "intPrimitive" -> Mcp.ToolProperty(`type` = "number", description = ""),
        "longPrimitive" -> Mcp.ToolProperty(`type` = "number", description = ""),
        "doublePrimitive" -> Mcp.ToolProperty(`type` = "number", description = ""),
        "floatPrimitive" -> Mcp.ToolProperty(`type` = "number", description = ""),
        "bytePrimitive" -> Mcp.ToolProperty(`type` = "number", description = ""),
        "boxedBoolean" -> Mcp.ToolProperty(`type` = "boolean", description = ""),
        "boxedInteger" -> Mcp.ToolProperty(`type` = "number", description = ""),
        "boxedLong" -> Mcp.ToolProperty(`type` = "number", description = ""),
        "boxedDouble" -> Mcp.ToolProperty(`type` = "number", description = ""),
        "boxedFloat" -> Mcp.ToolProperty(`type` = "number", description = ""),
        "boxedByte" -> Mcp.ToolProperty(`type` = "number", description = ""))

      result.required.toSet shouldEqual Set(
        "string",
        "booleanPrimitive",
        "intPrimitive",
        "longPrimitive",
        "doublePrimitive",
        "floatPrimitive",
        "bytePrimitive",
        "boxedBoolean",
        "boxedInteger",
        "boxedLong",
        "boxedDouble",
        "boxedFloat",
        "boxedByte")
    }

  }

}
