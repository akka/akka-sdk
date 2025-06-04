/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.impl.serialization.JsonSerializer;
import akka.javasdk.testkit.TestKitSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@ExtendWith(Junit5LogCapturing.class)
public class McpEndpointTest extends TestKitSupport {

  // MCP endpoint defined in akkajavasdk.components.mcp.TestMcpEndpoint

  @Test
  public void listTools() {
    var listingResult = httpClient.POST("/mcp")
        .withRequestBody(ContentTypes.APPLICATION_JSON,
            """
            {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
            """.getBytes(StandardCharsets.UTF_8)
        ).invoke();

    assertThat(listingResult.status()).isEqualTo(StatusCodes.OK);
    assertThat(listingResult.body().utf8String()).isEqualTo(
        """
        {"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"echo","description":"A method that returns what is fed to it","inputSchema":{"type":"object","properties":{"echo":{"type":"string","description":"the string to echo"}},"required":["echo"]}}]}}
        """.trim()
    );
  }

  @Test
  public void callATool() {
    var listingResult = httpClient.POST("/mcp")
        .withRequestBody(ContentTypes.APPLICATION_JSON,
            """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"echo","arguments":{"echo":"hello world"}}}}}
            """.getBytes(StandardCharsets.UTF_8)
        ).invoke();

    assertThat(listingResult.status()).isEqualTo(StatusCodes.OK);
    assertThat(listingResult.body().utf8String()).isEqualTo(
        """
        {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"hello world"}],"isError":false}}
        """.trim()
    );
  }

  @Test
  public void listResourceTemplates() {
    var listingResult = httpClient.POST("/mcp")
        .withRequestBody(ContentTypes.APPLICATION_JSON,
            """
            {
              "jsonrpc": "2.0",
              "id": 3,
              "method": "resources/templates/list"
            }
            """.getBytes(StandardCharsets.UTF_8)
        ).invoke();

    assertThat(listingResult.status()).isEqualTo(StatusCodes.OK);
    assertThat(listingResult.body().utf8String()).isEqualTo(
        """
        {"jsonrpc":"2.0","id":3,"result":{"resourceTemplates":[{"uriTemplate":"file:///dynamic/{path}","name":"Dynamic resource","mimeType":"text/plain"}]}}
        """.trim()
    );
  }

  @Test
  public void fetchResourceTemplate() {
    var listingResult = httpClient.POST("/mcp")
        .withRequestBody(ContentTypes.APPLICATION_JSON,
            """
            {"jsonrpc": "2.0","id": 2,"method": "resources/read","params": {"uri": "file:///dynamic/example.txt"}}
            """.getBytes(StandardCharsets.UTF_8)
        ).invoke();

    assertThat(listingResult.status()).isEqualTo(StatusCodes.OK);
    assertThat(listingResult.body().utf8String()).isEqualTo("""
       {"jsonrpc":"2.0","id":2,"result":{"contents":[{"text":"This is a dynamic resource for example.txt","uri":"file:///dynamic/example.txt","mimeType":"text/plain"}]}}
       """.trim()
    );
  }

  @Test
  public void listResources() {
    var listingResult = httpClient.POST("/mcp")
        .withRequestBody(ContentTypes.APPLICATION_JSON,
            """
            {"jsonrpc":"2.0","id":1,"method":"resources/list","params":{}}
            """.getBytes(StandardCharsets.UTF_8)
        ).invoke();

    assertThat(listingResult.status()).isEqualTo(StatusCodes.OK);
    assertThat(listingResult.body().utf8String()).isEqualTo(
        """
        {"jsonrpc":"2.0","id":1,"result":{"resources":[{"uri":"file:///example.pdf","name":"example binary resource","description":"a pdf as sample of a binary resource","mimeType":"application/pdf"},{"uri":"file:///example.txt","name":"example text","description":"a sample text file","mimeType":"text/plain"}]}}
        """.trim()
    );
  }

  @Test
  public void fetchTextResource() {
    var listingResult = httpClient.POST("/mcp")
        .withRequestBody(ContentTypes.APPLICATION_JSON,
            """
            {"jsonrpc": "2.0","id": 2,"method": "resources/read","params": {"uri": "file:///example.txt"}}
            """.getBytes(StandardCharsets.UTF_8)
        ).invoke();

    assertThat(listingResult.status()).isEqualTo(StatusCodes.OK);
    assertThat(listingResult.body().utf8String()).isEqualTo("""
       {"jsonrpc":"2.0","id":2,"result":{"contents":[{"text":"This is an example resource","uri":"file:///example.txt","mimeType":"text/plain"}]}}
       """.trim()
    );
  }

  @Test
  public void fetchBinaryResource() throws Exception {
    var listingResult = httpClient.POST("/mcp")
        .withRequestBody(ContentTypes.APPLICATION_JSON,
            """
            {"jsonrpc": "2.0","id": 2,"method": "resources/read","params": {"uri": "file:///example.pdf"}}
            """.getBytes(StandardCharsets.UTF_8)
        ).invoke();

    assertThat(listingResult.status()).isEqualTo(StatusCodes.OK);

    var jsonTree = JsonSerializer.internalObjectMapper().readValue(listingResult.body().utf8String(), JsonNode.class);
    if (jsonTree.has("error")) {
      fail("Response message [" + listingResult.body().utf8String() + "] is an error");
    }
    assertThat(jsonTree.get("result").get("contents")).hasSize(1);
    assertThat(jsonTree.get("result").get("contents").get(0).has("blob")).isTrue();
    assertThat(jsonTree.get("result").get("contents").get(0).get("mimeType").asText()).isEqualTo("application/pdf");
  }

  @Test
  public void listPrompts() {
    var listingResult = httpClient.POST("/mcp")
        .withRequestBody(ContentTypes.APPLICATION_JSON,
            """
            {"jsonrpc":"2.0","id":1,"method":"prompts/list","params":{}}
            """.getBytes(StandardCharsets.UTF_8)
        ).invoke();

    assertThat(listingResult.status()).isEqualTo(StatusCodes.OK);
    assertThat(listingResult.body().utf8String()).isEqualTo(
        """
        {"jsonrpc":"2.0","id":1,"result":{"prompts":[{"name":"code_review","description":"Code review prompt","arguments":[{"name":"code","description":"a prompt argument","required":true}]}]}}
        """.trim()
    );
  }

  @Test
  public void callAPrompt() {
    var listingResult = httpClient.POST("/mcp")
        .withRequestBody(ContentTypes.APPLICATION_JSON,
            """
            {
             "jsonrpc": "2.0",
             "id": 2,
             "method": "prompts/get",
             "params": {
               "name": "code_review",
               "arguments": {
                 "code": "def hello():\\n    print('world')"
               }
             }
           }
           """.getBytes(StandardCharsets.UTF_8)
        ).invoke();

    assertThat(listingResult.status()).isEqualTo(StatusCodes.OK);
    assertThat(listingResult.body().utf8String()).isEqualTo(
        """
        {"jsonrpc":"2.0","id":2,"result":{"description":"","messages":[{"content":{"type":"text","text":"Please review this Python code:\\\\ndef hello():\\n    print('world')"},"role":"user"}]}}
        """.trim()
    );
  }
}
