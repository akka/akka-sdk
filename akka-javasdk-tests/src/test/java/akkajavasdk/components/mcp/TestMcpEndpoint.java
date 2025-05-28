/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.mcp;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.mcp.*;
import akka.javasdk.mcp.AbstractMcpEndpoint;

import java.io.IOException;

@McpEndpoint(serverName = "test")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@McpResourceTemplate(
    uriTemplate = "file:///{path}",
    name = "Project Files",
    description = "Access files in the project directory",
    mimeType = "application/octet-stream"
)
public class TestMcpEndpoint extends AbstractMcpEndpoint {

  @McpTool(description = "A method that returns what is fed to it")
  public String echo(@Description("the string to echo") String echo) {
    return echo;
  }

  @McpResource(name = "example text", uri = "file:///example.txt", description = "a sample text file")
  public String exampleTxt() {
    return "This is an example resource";
  }

  @McpResource(name = "example binary resource", uri = "file:///example.pdf", description = "a pdf as sample of a binary resource", mimeType = "application/pdf")
  public byte[] exampleBytes() throws IOException {
    return getClass().getResourceAsStream("/static-resources/sample-pdf-file.pdf").readAllBytes();
  }

  @McpPrompt(name = "code_review", description = "Code review prompt")
  public String pythonCodeReview(@Description("a prompt argument") String code) {
    return "Please review this Python code:\\n" + code;
  }
}
