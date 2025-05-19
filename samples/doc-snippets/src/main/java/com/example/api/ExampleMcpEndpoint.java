package com.example.api;

import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpResource;
import akka.javasdk.annotations.mcp.McpTool;
import akka.javasdk.annotations.mcp.McpToolParameterDescription;

import java.io.IOException;
import java.io.InputStream;

@McpEndpoint(serverName = "doc-snippets-mcp-sample", serverVersion = "0.0.1")
public class ExampleMcpEndpoint {

  public record EchoToolRequest(String message) {}

  @McpTool(name = "echo", description = "Echoes back whatever string is thrown at it", inputSchema = """
      {
        "type":"object",
        "properties": {
          "message": {"type":"string", "description":"A string to echo"}
         },
         "required": ["message"]
      }
      """)
  public String echo(EchoToolRequest input) {
    return input.message;
  }


  // example of reflectively deduced input schema, still needs descriptions
  public record EchoToolRequest2(
      @McpToolParameterDescription("The first number")
      int n1,
      @McpToolParameterDescription("The second number")
      int n2
      ) {}

  @McpTool(name = "multiply", description = "Multiplies the two given numbers and returns the result")
  public String multiply(EchoToolRequest2 input) {
    var result = input.n1 * input.n2;
    return Integer.toString(result);
  }

  @McpResource(uri = "file://background.png", name = "Background image", description = "A background image for Akka sites", mimeType = "image/png")
  public byte[] backgroundImage() {
    try (InputStream in = this.getClass().getResourceAsStream("/static-resources/images/background.png")) {
      if (in == null) throw new RuntimeException("Could not find background image");
      return in.readAllBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
