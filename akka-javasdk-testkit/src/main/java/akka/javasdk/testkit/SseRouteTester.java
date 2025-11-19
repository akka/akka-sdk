/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.http.javadsl.model.sse.ServerSentEvent;
import java.time.Duration;
import java.util.List;

public interface SseRouteTester {

  /**
   * @param path A path in the service that responds with SSE
   * @param count A number of events to wait for
   * @return {@code count} events that the endpoint emitted before the timeout hit
   * @throws java.util.concurrent.TimeoutException if the service did not emit {@code count} events
   *     before the timeout hit
   */
  List<ServerSentEvent> receiveFirstN(String path, int count, Duration timeout);

  /**
   * @param path A path in the service that responds with SSE
   * @param count A number of events to wait for
   * @param startFromId An SSE id to pass to the endpoint as point to resume from
   * @return {@code count} events that the endpoint emitted before the timeout hit
   * @throws java.util.concurrent.TimeoutException if the service did not emit {@code count} events
   *     before the timeout hit
   */
  List<ServerSentEvent> receiveNFromOffset(
      String path, int count, String startFromId, Duration timeout);
}
