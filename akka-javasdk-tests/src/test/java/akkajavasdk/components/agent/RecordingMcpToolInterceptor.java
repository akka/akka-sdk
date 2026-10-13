/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.RemoteMcpTools;
import com.typesafe.config.Config;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Records each MCP tool response it sees, and prefixes the response text, so a test can check that
 * the error flag of a result survives a rewrite of its text.
 */
public class RecordingMcpToolInterceptor implements RemoteMcpTools.ToolInterceptor {

  public static final String PREFIX = "intercepted: ";

  public record SeenResponse(String agent, String toolName, boolean isError) {}

  private static final Queue<SeenResponse> SEEN = new ConcurrentLinkedQueue<>();

  public static List<SeenResponse> seen(String agent) {
    return SEEN.stream().filter(response -> response.agent().equals(agent)).toList();
  }

  public static void reset() {
    SEEN.clear();
  }

  /** The always failing tool of the MCP endpoint of this service, see TestMcpEndpoint. */
  public static RemoteMcpTools failingTool(Config config, String agent) {
    var port = config.getInt("akka.javasdk.testkit.http-port");
    return RemoteMcpTools.fromServer("http://localhost:" + port + "/mcp")
        .withAllowedToolNames("fail")
        .withToolInterceptor(new RecordingMcpToolInterceptor(agent));
  }

  private final String agent;

  private RecordingMcpToolInterceptor(String agent) {
    this.agent = agent;
  }

  @Override
  public String interceptResponse(
      RemoteMcpTools.ToolInterceptorContext context,
      String requestPayloadJson,
      String responsePayload) {
    SEEN.add(new SeenResponse(agent, context.toolName(), context.isError()));
    return PREFIX + responsePayload;
  }
}
