/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.annotation.DoNotInherit;
import akka.http.javadsl.model.HttpHeader;
import akka.javasdk.impl.agent.RemoteMcpToolsImpl;

import java.util.function.Predicate;

/**
 * Access to tools from one remote MCP server.
 *
 * <p>Not for user extension, create instances using {@link #fromServer(String)}.
 */
@DoNotInherit
public interface RemoteMcpTools {

  /**
   * @param serverUri A URI to the remote MCP HTTP server, for example "https://example.com/sse" or
   *     "https://example.com/mcp
   */
  static RemoteMcpTools fromServer(String serverUri) {
    return new RemoteMcpToolsImpl(serverUri);
  }

  /**
   * Define a filter to select what discovered tool names are passed on to the chat model. Names
   * that are filtered will not be described to the model and will not allow calls.
   */
  RemoteMcpTools allowToolNames(Predicate<String> toolNameFilter);

  /**
   * Specify an interceptor that has the capability to allow or deny calls (by throwing an
   * exception) and also to modify and filter input to the MCP server tool.
   */
  RemoteMcpTools withToolInterceptor(ToolInterceptor interceptor);

  /**
   * @param header A header that should be passed with each call to the MCP server, for example some
   *     authentication token in an {@link akka.http.javadsl.model.headers.OAuth2BearerToken}
   */
  RemoteMcpTools addClientHeader(HttpHeader header);

  /**
   * Context details about the intercepted MCP tool call.
   *
   * <p>Not for user extension.
   */
  @DoNotInherit
  interface ToolInterceptorContext {
    String mcpServerUri();

    String toolName();
  }

  interface ToolInterceptor {
    /**
     * Intercept calls to tools before they are executed, disallowing the call based on the payload
     * can be done by throwing an exception, modifying the payload is also possible. When modifying
     * the payload, you need to make sure the payload still fulfills the schema of the tool with
     * required fields and correct field types.
     *
     * @param context Details about the intercepted tool call
     * @param requestPayloadJson The tool request payload in a Json string.
     */
    default String interceptRequest(ToolInterceptorContext context, String requestPayloadJson) {
      return requestPayloadJson;
    }

    /**
     * Intercept responses from MCP tools, disallowing the call based on the result can be done by
     * throwing an exception, modifying the result is also possible. When modifying the result, you
     * need to make sure the payload still is something the model will understand.
     *
     * @param context Details about the intercepted tool call
     * @param requestPayloadJson The request payload as passed to the MCP tool (possibly modified by
     *     {@link #interceptRequest(ToolInterceptorContext, String)})
     */
    default String interceptResponse(
        ToolInterceptorContext context, String requestPayloadJson, String responsePayload) {
      return responsePayload;
    };
  }
}
