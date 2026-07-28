/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.concurrent.ExecutionContext

import akka.javasdk.impl.mcp.TestMcpEndpoints
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class McpEndpointDescriptorFactorySpec extends AnyWordSpec with Matchers {

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
  }
}
