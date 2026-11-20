/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

/**
 * The remote A2A agent answered with a protocol error.
 *
 * <p>The code is the JSON-RPC error code, such as {@code -32001} for a task that does not exist.
 * For the HTTP+JSON binding, it is the code the A2A specification assigns to the error reason.
 */
public final class A2aRemoteErrorException extends A2aClientException {

  private final int code;
  private final String reason;

  public A2aRemoteErrorException(int code, String reason, String message) {
    super(message);
    this.code = code;
    this.reason = reason;
  }

  /** The error code. */
  public int code() {
    return code;
  }

  /** The A2A error reason, such as {@code TASK_NOT_FOUND}, or {@code null} when it is not known. */
  public String reason() {
    return reason;
  }
}
