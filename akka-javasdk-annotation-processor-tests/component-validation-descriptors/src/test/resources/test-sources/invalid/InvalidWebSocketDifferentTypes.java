/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package invalid;

import akka.NotUsed;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.WebSocket;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;

@HttpEndpoint("invalid-websocket")
public class InvalidWebSocketDifferentTypes {

  @WebSocket("/echo")
  public Flow<String, ByteString, NotUsed> differentInOut() {
    return null;
  }
}
