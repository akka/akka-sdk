/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

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
        {"jsonrpc":"2.0","id":1,"result":{"resources":[{"uri":"file:///example.txt","name":"exampletxt","description":"a sample text file","mimeType":"text/plain"}]}}
        """.trim()
    );
  }

  @Test
  public void fetchResource() {
    var listingResult = httpClient.POST("/mcp")
        .withRequestBody(ContentTypes.APPLICATION_JSON,
            """
            {"jsonrpc": "2.0","id": 2,"method": "resources/read","params": {"uri": "file:///example.txt"}}
            """.getBytes(StandardCharsets.UTF_8)
        ).invoke();

    assertThat(listingResult.status()).isEqualTo(StatusCodes.OK);
    assertThat(listingResult.body().utf8String()).isEqualTo(
        """
        {"jsonrpc":"2.0","id":2,"result":{"contents":[{"text":"This is an example resource","uri":"file:///example.txt","mimeType":"text/plain"}]}}
        """.trim()
    );
  }
}
