/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.javasdk.impl.JsonRpc.JsonRpcErrorResponse
import akka.javasdk.impl.JsonRpc.JsonRpcSuccessResponse
import akka.javasdk.impl.JsonRpc.NumberId
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.HttpRequestHeaders
import akka.runtime.sdk.spi.JwtClaims
import akka.runtime.sdk.spi.RequestPrincipal
import akka.util.ByteString
import io.opentelemetry.api.trace.Span
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.CompletableFuture
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.jdk.FutureConverters.CompletionStageOps

class McpSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers with LogCapturing {

  "The stateless MCP server" should {
    "accept init request" in new WithRequestThroughHandler {
      override def request: Mcp.McpRequest = Mcp.InitializeRequest(
        protocolVersion = Mcp.ProtocolVersion,
        capabilities = Mcp.ClientCapabilities(experimental = Map.empty, roots = None, sampling = Map.empty),
        clientInfo = Mcp.Implementation(name = "McpSpec", version = "2000"),
        _meta = None)

      override def requestMethod: String = Mcp.InitializeRequest.method

      httpResponse.entity.contentType should be(ContentTypes.`application/json`)
      jsonRpcResponse.asInstanceOf[JsonRpc.JsonRpcSuccessResponse].id should be(jsonRpcRequest.requestId.get)
      val mcpResult = Mcp.result[Mcp.InitializeResult](jsonRpcResponse)
      mcpResult.protocolVersion should be(Mcp.ProtocolVersion)
    }

    "parse resource/list request" in {
      val reqJson = """{
                      |  "jsonrpc": "2.0",
                      |  "id": 1,
                      |  "method": "resources/list",
                      |  "params": {
                      |    "cursor": "optional-cursor-value"
                      |  }
                      |}""".stripMargin
      val jsonRpcRequests = JsonRpc.Serialization.parseRequest(ByteString(reqJson))
      jsonRpcRequests should have size 1
      val jsonRpcRequest = jsonRpcRequests.head
      jsonRpcRequest.method should be("resources/list")
      val mcpRequest = Mcp.extractRequest[Mcp.ListResourcesRequest](jsonRpcRequest)
      mcpRequest.cursor should be(Some("optional-cursor-value"))
    }

    "render empty resource/list result" in {
      val result = Mcp.ListResourcesResult(Seq(), _meta = None, nextCursor = None)
      val jsonRpcResponse = Mcp.toJsonRpc(NumberId(1), result).asInstanceOf[JsonRpc.JsonRpcSuccessResponse]
      jsonRpcResponse.id shouldBe NumberId(1)
      val resultMap =
        jsonRpcResponse.result.get.asInstanceOf[Map[String, AnyRef]]
      val resources = resultMap("resources").asInstanceOf[List[Map[String, AnyRef]]]
      resources should have size 0
    }

    "render resource/list result with payload" in {
      val result = Mcp.ListResourcesResult(
        Seq(
          Mcp.Resource(
            uri = "text",
            name = "name",
            description = None,
            mimeType = Some("text/plain"),
            annotations = None,
            size = Some(5L))),
        _meta = None,
        nextCursor = None)
      val jsonRpcResponse = Mcp.toJsonRpc(NumberId(1), result).asInstanceOf[JsonRpc.JsonRpcSuccessResponse]
      jsonRpcResponse.id shouldBe NumberId(1)
      val resultMap =
        jsonRpcResponse.result.get.asInstanceOf[Map[String, AnyRef]]
      val resources = resultMap("resources").asInstanceOf[List[Map[String, AnyRef]]]
      resources should have size 1
      resources.head.get("uri") should be(Some("text"))
    }

    "parse resource/read request with payload" in {
      val reqJson =
        """{"jsonrpc":"2.0","id":1,"method":"resources/read","params":{"_meta":{"progressToken":1},"uri":"fixme"}}""".stripMargin

      val jsonRpcRequests = JsonRpc.Serialization.parseRequest(ByteString(reqJson))
      jsonRpcRequests should have size 1
      val jsonRpcRequest = jsonRpcRequests.head
      jsonRpcRequest.method should be("resources/read")
      val mcpRequest = Mcp.extractRequest[Mcp.ReadResourceRequest](jsonRpcRequest)
      mcpRequest.uri should be("fixme")
      mcpRequest._meta should be(Some(Mcp.Meta(progressToken = Some(1))))
    }

    "render resource/read result with payload" in {
      val result =
        Mcp.ReadResourceResult(Seq(Mcp.TextResourceContents("text", uri = "example", mimeType = "plain/text")), None)
      val jsonRpcResponse = Mcp.toJsonRpc(NumberId(1), result).asInstanceOf[JsonRpcSuccessResponse]
      jsonRpcResponse.id shouldBe NumberId(1)
      jsonRpcResponse shouldBe a[JsonRpc.JsonRpcSuccessResponse]
      val resultMap =
        jsonRpcResponse.result.get.asInstanceOf[Map[String, AnyRef]]
      resultMap.get("contents") should be(
        Some(Seq(Map("text" -> "text", "uri" -> "example", "mimeType" -> "plain/text"))))
    }

    "return expecter error response for unknown resource" in new WithRequestThroughHandler {
      override def request: Mcp.McpRequest = Mcp.ReadResourceRequest(uri = "file://unknown", _meta = None)
      override def requestMethod: String = Mcp.ReadResourceRequest.method
      jsonRpcResponse.asInstanceOf[JsonRpcErrorResponse].error.requestId should be(jsonRpcRequest.requestId)
      jsonRpcResponse shouldBe a[JsonRpc.JsonRpcErrorResponse]
      val errorResponse = jsonRpcResponse.asInstanceOf[JsonRpc.JsonRpcErrorResponse]

      errorResponse.error.message should be("Resource not found")
      errorResponse.error.code shouldBe Mcp.ErrorCodes.ResourceNotFound
      errorResponse.error.data should be(Some(Map("uri" -> "file://unknown")))
    }

    "render empty resource/template/list result" in {
      val result = Mcp.ListResourceTemplateResult(Seq(), _meta = None, nextCursor = None)
      val jsonRpcResponse = Mcp.toJsonRpc(NumberId(1), result).asInstanceOf[JsonRpcSuccessResponse]
      jsonRpcResponse.id shouldBe NumberId(1)
      val resultMap =
        jsonRpcResponse.result.get.asInstanceOf[Map[String, AnyRef]]
      val resourceTemplates = resultMap("resourceTemplates").asInstanceOf[List[Map[String, AnyRef]]]
      resourceTemplates should have size 0
      JsonRpc.Serialization.responseToJsonBytes(jsonRpcResponse).utf8String should be(
        """{"jsonrpc":"2.0","id":1,"result":{"resourceTemplates":[]}}""")
    }

    "parse tools/list request" in {
      val reqJson =
        """{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}"""

      val jsonRpcRequests = JsonRpc.Serialization.parseRequest(ByteString(reqJson))
      jsonRpcRequests should have size 1
      val jsonRpcRequest = jsonRpcRequests.head
      jsonRpcRequest.method should be("tools/list")
      Mcp.extractRequest[Mcp.ListToolsRequest](jsonRpcRequest)
    }

    "render empty tools/list result" in {
      val result = Mcp.ListToolsResult(Seq())
      val jsonRpcResponse = Mcp.toJsonRpc(NumberId(1), result).asInstanceOf[JsonRpc.JsonRpcSuccessResponse]
      jsonRpcResponse.id shouldBe NumberId(1)
      val resultMap =
        jsonRpcResponse.result.get.asInstanceOf[Map[String, AnyRef]]
      val resourceTemplates = resultMap("tools").asInstanceOf[List[Map[String, AnyRef]]]
      resourceTemplates should have size 0
      JsonRpc.Serialization.responseToJsonBytes(jsonRpcResponse).utf8String should be(
        """{"jsonrpc":"2.0","id":1,"result":{"tools":[]}}""")
    }

    "render tools/list result" in {
      val result = Mcp.ListToolsResult(
        Seq(Mcp.ToolDescription(
          name = "tool-name",
          description = "a tool that does this or that or both",
          inputSchema = Mcp.InputSchema(
            properties = Map("propertyA" -> Mcp.ToolProperty(`type` = "boolean", "a flag")),
            required = Seq("propertyA")))))
      val jsonRpcResponse = Mcp.toJsonRpc(NumberId(1), result)
      jsonRpcResponse.asInstanceOf[JsonRpc.JsonRpcSuccessResponse].id shouldBe NumberId(1)
      jsonRpcResponse shouldBe a[JsonRpc.JsonRpcSuccessResponse]
      val resultMap =
        jsonRpcResponse.asInstanceOf[JsonRpc.JsonRpcSuccessResponse].result.get.asInstanceOf[Map[String, AnyRef]]
      val resourceTemplates = resultMap("tools").asInstanceOf[List[Map[String, AnyRef]]]
      resourceTemplates should have size 1
      JsonRpc.Serialization.responseToJsonBytes(jsonRpcResponse).utf8String should be(
        """{"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"tool-name","description":"a tool that does this or that or both","inputSchema":{"type":"object","properties":{"propertyA":{"type":"boolean","description":"a flag"}},"required":["propertyA"]}}]}}""")
    }

    "call a tool" in new WithRequestThroughHandler {
      override def mcpDescriptor = Mcp.McpDescriptor(tools =
        Seq(Mcp.ToolDescription("my-tool", "a tool I made", Mcp.InputSchema(properties = Map.empty)) -> {
          (input: Map[String, Any]) =>
            Future.successful(Mcp.CallToolResult(Seq(Mcp.TextContent("Oboy oboy!"))))
        }))
      override def request = Mcp.CallToolRequest("my-tool", Map.empty)
      override def requestMethod = Mcp.CallToolRequest.method

      jsonRpcResponse shouldBe a[JsonRpc.JsonRpcSuccessResponse]
      jsonRpcResponse.asInstanceOf[JsonRpc.JsonRpcSuccessResponse].result.get shouldBe a[Map[_, _]]
      val resultMap =
        jsonRpcResponse.asInstanceOf[JsonRpc.JsonRpcSuccessResponse].result.get.asInstanceOf[Map[String, AnyRef]]
      resultMap.get("content") should be(Some(Seq(Map("text" -> "Oboy oboy!", "type" -> "text"))))

      // Note: Parsing this would require special deserialization of tool result payload to determine concrete content subtype.
      // Leaving it alone for now since we don't really need it unless we want to implement MCP clients
      // val mcpResult = Mcp.result[Mcp.CallToolResult](jsonRpcResponse)
      // mcpResult.content should have size 1
      // mcpResult.content.head shouldBe a[Mcp.TextContent]
      // mcpResult.content.head.asInstanceOf[Mcp.TextContent].text should be("Oboy oboy!")
    }

  }

  trait WithRequestThroughHandler {
    def request: Mcp.McpRequest
    def requestMethod: String
    def mcpDescriptor: Mcp.McpDescriptor = Mcp.McpDescriptor()

    val httpEndpointDescriptor = new Mcp.StatelessMcpEndpoint(mcpDescriptor).httpEndpoint()
    httpEndpointDescriptor.methods should have size 1

    val jsonRpcRequest = Mcp.request(requestMethod, Some(request))
    val requestString = JsonRpc.Serialization.requestToJsonString(jsonRpcRequest)

    val strictEntity = HttpEntity.Strict(ContentTypes.`application/json`, ByteString(requestString))
    val clientHttpRequest =
      HttpRequest(uri = "http://localhost/mpc", entity = strictEntity)

    val endpointInstance = httpEndpointDescriptor.instanceFactory(new HttpEndpointConstructionContext {
      override def openTelemetrySpan: Option[Span] = None
      override def principal: RequestPrincipal = RequestPrincipal.empty
      override def jwt: Option[JwtClaims] = None
      override def requestHeaders: HttpRequestHeaders = new HttpRequestHeaders {
        override def header(name: String): Option[HttpHeader] = None
        override def allHeaders: Seq[HttpHeader] = Seq.empty
      }

      override def httpRequest: HttpRequest = clientHttpRequest
    })

    val httpResponse = httpEndpointDescriptor.methods.head.userMethod
      .invoke(endpointInstance, strictEntity)
      .asInstanceOf[CompletableFuture[HttpResponse]]
      .asScala
      .futureValue

    val responseJsonText = httpResponse.entity.toStrict(3.seconds).futureValue.data.utf8String
    val jsonRpcResponse = JsonRpc.Serialization.parseResponse(responseJsonText)

  }

}
