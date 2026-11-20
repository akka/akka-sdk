/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import java.util.Optional;

/**
 * The JSON-RPC error codes that A2A uses: the five standard JSON-RPC codes and the A2A codes from
 * -32001 to -32009.
 */
public enum A2aErrorCode {
  JSON_PARSE_ERROR(-32700, "Invalid JSON payload", "JSON_PARSE_ERROR"),
  INVALID_REQUEST(-32600, "Request payload validation error", "INVALID_REQUEST"),
  METHOD_NOT_FOUND(-32601, "Method not found", "METHOD_NOT_FOUND"),
  INVALID_PARAMS(-32602, "Invalid parameters", "INVALID_PARAMS"),
  INTERNAL_ERROR(-32603, "Internal error", "INTERNAL_ERROR"),
  TASK_NOT_FOUND(-32001, "Task not found", "TASK_NOT_FOUND"),
  TASK_NOT_CANCELABLE(-32002, "Task cannot be canceled", "TASK_NOT_CANCELABLE"),
  PUSH_NOTIFICATION_NOT_SUPPORTED(
      -32003, "Push notifications are not supported", "PUSH_NOTIFICATION_NOT_SUPPORTED"),
  UNSUPPORTED_OPERATION(-32004, "This operation is not supported", "UNSUPPORTED_OPERATION"),
  CONTENT_TYPE_NOT_SUPPORTED(-32005, "Incompatible content types", "CONTENT_TYPE_NOT_SUPPORTED"),
  INVALID_AGENT_RESPONSE(-32006, "Invalid agent response", "INVALID_AGENT_RESPONSE"),
  EXTENDED_AGENT_CARD_NOT_CONFIGURED(
      -32007, "Extended agent card not configured", "EXTENDED_AGENT_CARD_NOT_CONFIGURED"),
  EXTENSION_SUPPORT_REQUIRED(-32008, "Extension support required", "EXTENSION_SUPPORT_REQUIRED"),
  VERSION_NOT_SUPPORTED(-32009, "Version not supported", "VERSION_NOT_SUPPORTED");

  /** The {@code domain} of the {@code google.rpc.ErrorInfo} detail of an A2A error. */
  public static final String ERROR_DOMAIN = "a2a-protocol.org";

  private final int code;
  private final String message;
  private final String reason;

  A2aErrorCode(int code, String message, String reason) {
    this.code = code;
    this.message = message;
    this.reason = reason;
  }

  /** The JSON-RPC error code. */
  public int code() {
    return code;
  }

  /** The default message. */
  public String message() {
    return message;
  }

  /** The {@code reason} of the {@code google.rpc.ErrorInfo} detail, for example TASK_NOT_FOUND. */
  public String reason() {
    return reason;
  }

  /** True for the codes that A2A defines, as opposed to the standard JSON-RPC codes. */
  public boolean a2aSpecific() {
    return code <= -32001 && code >= -32099;
  }

  public static Optional<A2aErrorCode> fromCode(int code) {
    for (A2aErrorCode value : values()) {
      if (value.code == code) return Optional.of(value);
    }
    return Optional.empty();
  }
}
