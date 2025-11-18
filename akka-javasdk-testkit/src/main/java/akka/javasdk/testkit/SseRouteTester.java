/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.sse.ServerSentEvent;
import java.time.Duration;
import java.util.List;

public interface SseRouteTester {

  /**
   * @param path A path in the service that responds with SSE
   * @param count A number of events to wait for
   * @return {count} events that the endpoint emitted before the timeout hit
   * @throws {@link java.util.concurrent.TimeoutException} if the service did not emit {count}
   *     events before the timeout hit
   */
  List<ServerSentEvent> receiveFirstN(String path, HttpMethod method, int count, Duration timeout);

  /**
   * @param path A path in the service that responds with SSE
   * @param count A number of events to wait for
   * @param startFromId A SSE id to pass to the endpoint as point to resume from
   * @return {count} events that the endpoint emitted before the timeout hit
   * @throws {@link java.util.concurrent.TimeoutException} if the service did not emit {count}
   *     events before the timeout hit
   */
  List<ServerSentEvent> receiveNFromOffset(
      String path, HttpMethod method, int count, String startFromId, Duration timeout);
}
