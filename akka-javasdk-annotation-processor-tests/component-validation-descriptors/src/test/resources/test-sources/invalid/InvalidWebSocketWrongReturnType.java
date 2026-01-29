/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package invalid;

import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.WebSocket;

@HttpEndpoint("invalid-websocket")
public class InvalidWebSocketWrongReturnType {

  @WebSocket("/echo")
  public String wrongReturnType() {
    return "wrong";
  }
}
