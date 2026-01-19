/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package invalid;

import akka.NotUsed;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.WebSocket;
import akka.stream.javadsl.Flow;

@HttpEndpoint("invalid-websocket")
public class InvalidWebSocketUnsupportedMessageType {

  @WebSocket("/echo")
  public Flow<Integer, Integer, NotUsed> unsupportedType() {
    return null;
  }
}
