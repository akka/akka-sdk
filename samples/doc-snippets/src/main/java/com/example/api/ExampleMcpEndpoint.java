package com.example.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.mcp.*;
import akka.javasdk.client.ComponentClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

// tag::endpoint-class[]
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@McpEndpoint(
    serverName = "doc-snippets-mcp-sample",
    serverVersion = "0.0.1")
public class ExampleMcpEndpoint {
  private ComponentClient componentClient;

  public ExampleMcpEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }
  // end::endpoint-class[]


  // tag::tool-manual-input-schema[]
  public record EchoToolRequest(String message) {}

  @McpTool(
      name = "echo",
      description = "Echoes back whatever string is thrown at it",
      inputSchema = """
      {
        "type":"object",
        "properties": {
          "message": {"type":"string", "description":"A string to echo"}
         },
         "required": ["message"]
      }
      """ // <1>
    )
  public String echo(EchoToolRequest input) {
    return input.message;
  }
  // end::tool-manual-input-schema[]


  // tag::tool[]
  @McpTool(
      name = "add", // <2>
      description = "Adds the two given numbers and returns the result"
  )
  public String add(@Description("The first number") int n1, @Description("The second number") int n2) {
    var result = n1 + n2;
    return Integer.toString(result);
  }
  // end::tool[]


  // tag::tool-with-class[]
  // example of reflectively deduced input schema, still needs descriptions
  public record EchoToolRequest2(
      @Description("The first number") // <1>
      int n1,
      @Description("The second number")
      int n2,
      @Description("An optional third number")
      Optional<Integer> n3
      ) {}

  @McpTool(
      name = "multiply", // <2>
      description = "Multiplies the two given numbers and returns the result",
      annotations = {ToolAnnotation.ReadOnly, ToolAnnotation.ClosedWorld} // <3>
  )
  public String multiply(EchoToolRequest2 input) {
    var result = input.n1 * input.n2 * input.n3.orElse(1);
    return Integer.toString(result);
  }
  // end::tool-with-class[]

  // tag::resources[]
  @McpResource(uri = "file://background.png", name = "Background image", description = "A background image for Akka sites", mimeType = "image/png")
  public byte[] backgroundImage() {
    try (InputStream in = this.getClass().getResourceAsStream("/static-resources/images/background.png")) {
      if (in == null) throw new RuntimeException("Could not find background image");
      return in.readAllBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  // end::resources[]

  // tag::endpoint-class[]
}
// end::endpoint-class[]
