/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package valid;

import akka.NotUsed;
import akka.http.javadsl.model.ws.Message;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.WebSocket;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;

@HttpEndpoint("websocket")
public class ValidWebSocketEndpoint {

  @WebSocket("/text")
  public Flow<String, String, NotUsed> textWebSocket() {
    return Flow.create();
  }

  @WebSocket("/binary")
  public Flow<ByteString, ByteString, NotUsed> binaryWebSocket() {
    return Flow.create();
  }

  @WebSocket("/message")
  public Flow<Message, Message, NotUsed> messageWebSocket() {
    return Flow.create();
  }

  @WebSocket("/with-path-param/{id}")
  public Flow<String, String, NotUsed> withPathParam(String id) {
    return Flow.create();
  }
}
