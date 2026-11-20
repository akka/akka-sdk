/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A JSON-RPC 2.0 request or notification.
 *
 * @param jsonrpc must be {@code 2.0}
 * @param id a string, a number or null; absent for a notification, which has no response
 * @param method the method name, for example {@code SendMessage}
 * @param params the parameters, kept as JSON until the method is known
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcRequest(String jsonrpc, JsonNode id, String method, JsonNode params) {

  public static final String VERSION = "2.0";

  public static JsonRpcRequest of(JsonNode id, String method, JsonNode params) {
    return new JsonRpcRequest(VERSION, id, method, params);
  }

  /** True when the request has the members that JSON-RPC 2.0 requires. */
  public boolean valid() {
    return VERSION.equals(jsonrpc)
        && method != null
        && (id == null || id.isTextual() || id.isNumber() || id.isNull());
  }
}
