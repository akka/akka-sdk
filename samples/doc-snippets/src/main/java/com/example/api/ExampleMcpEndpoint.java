package com.example.api;

import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpResource;
import akka.javasdk.annotations.mcp.McpTool;

import java.io.IOException;
import java.io.InputStream;

@McpEndpoint(serverName = "doc-snippets-mcp-sample", serverVersion = "0.0.1")
public class ExampleMcpEndpoint {

  @McpTool(name = "echo", description = "Echoes back whatever string is thrown at it")
  public String echo(String input) {
    return input;
  }

  @McpResource(uri = "file://background.png", name = "Background image", description = "A background image for Akka sites", mimeType = "image/png")
  public byte[] backgroundImage() {
    try (InputStream in = this.getClass().getClassLoader()
             .getResourceAsStream("/static-resources/images/background.png")) {
      if (in == null) throw new RuntimeException("Could not find background image");
      return in.readAllBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
