package akka.javasdk.agent;

import akka.annotation.DoNotInherit;
import akka.http.javadsl.model.HttpHeader;
import akka.javasdk.impl.agent.RemoteMcpToolsImpl;

import java.util.function.Predicate;

/**
 * Access to tools from one remote MCP server.
 * <p>
 * Not for user extension, create instances using {@link #fromServer(String)}.
 */
@DoNotInherit
public interface RemoteMcpTools {

  /**
   * @param serverUri A URI to the remote MCP HTTP server, for example "https://example.com/sse" or "https://example.com/mcp
   */
  static RemoteMcpTools fromServer(String serverUri) {
    return new RemoteMcpToolsImpl(serverUri);
  }

  /**
   * Define a filter to select what discovered tool names are passed on to the chat model. Names that
   * are filtered will not be described to the model and will not allow calls.
   */
  RemoteMcpTools allowToolNames(Predicate<String> toolNameFilter);

  RemoteMcpTools withToolInterceptor(ToolInterceptor interceptor);

  RemoteMcpTools addClientHeader(HttpHeader header);

  interface ToolInterceptor {
    // FIXME appropriate Java types here - do we parse request payload into json, user types with jackson or what?

    /**
     * Intercept calls to tools before they are executed, disallowing the call based on the payload can be done by
     * throwing an exception, modifying the payload is also possible. When modifying the payload, you need to make
     * sure the payload still fulfills the schema of the tool with required fields and correct field types.
     */
    String interceptRequest(String toolName, String payload);

    /**
     * Intercept responses from MCP tools, disallowing the call based on the result can be done by
     * throwing an exception, modifying the result is also possible. When modifying the result, you need to make
     * sure the payload still is something the model will understand.
     */
    String interceptResponse(String toolName, String payload);
  }

}
