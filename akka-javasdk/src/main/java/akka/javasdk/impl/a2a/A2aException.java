/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;

/** INTERNAL API An A2A error, answered to the client with its code and message. */
@InternalApi
public final class A2aException extends RuntimeException {

  private final A2aErrorCode code;

  public A2aException(A2aErrorCode code, String message) {
    super(message, null, false, false);
    this.code = code;
  }

  public A2aErrorCode code() {
    return code;
  }
}
