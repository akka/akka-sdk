/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package valid;

import akka.NotUsed;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.WebSocket;
import akka.stream.javadsl.Flow;

@HttpEndpoint("invalid-websocket-body-param")
public class InvalidWebSocketBodyParam {

  public record AThing(String someProperty) {}

  @WebSocket("/echo/{id}")
  public Flow<String, String, NotUsed> withBodyParam(String id, AThing aThing) {
    return Flow.create();
  }

}
