/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * A JSON-RPC 2.0 response. Exactly one of {@code result} and {@code error} is set.
 *
 * @param jsonrpc must be {@code 2.0}
 * @param id the id of the request, or null when the request had no readable id
 * @param result the result, kept as JSON
 * @param error the error
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse(String jsonrpc, JsonNode id, JsonNode result, JsonRpcError error) {

  public static JsonRpcResponse success(JsonNode id, JsonNode result) {
    return new JsonRpcResponse(JsonRpcRequest.VERSION, idOrNull(id), result, null);
  }

  public static JsonRpcResponse failure(JsonNode id, JsonRpcError error) {
    return new JsonRpcResponse(JsonRpcRequest.VERSION, idOrNull(id), null, error);
  }

  // The id member is required in a response, and is null when the request id could not be read.
  private static JsonNode idOrNull(JsonNode id) {
    return id == null ? NullNode.getInstance() : id;
  }
}
