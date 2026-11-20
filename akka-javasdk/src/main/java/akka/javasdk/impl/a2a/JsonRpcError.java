/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The error member of a JSON-RPC response.
 *
 * @param code the error code
 * @param message a short description
 * @param data A2A sends an array of detail objects here, each with an {@code @type} member
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcError(int code, String message, JsonNode data) {

  public static JsonRpcError of(A2aErrorCode code) {
    return new JsonRpcError(code.code(), code.message(), null);
  }

  public static JsonRpcError of(A2aErrorCode code, String message, JsonNode data) {
    return new JsonRpcError(code.code(), message, data);
  }
}
