/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.util.ByteString;
import java.util.Optional;

public interface WebSocketRouteTester {

  record WsConnection<T>(
      TestPublisher.Probe<T> publisher,
      TestSubscriber.Probe<T> subscriber,
      Optional<String> chosenProtocol) {}

  /**
   * @param path A path to a web socket endpoint in the service
   * @return A pair of test probes, TestPublisher.Probe for sending messages to the endpoint and
   *     TestSubscriber.Probe for expecting messages from the service
   */
  WsConnection<String> wsTextConnection(String path);

  /**
   * @param path A path to a web socket endpoint in the service
   * @param protocol Request the server websocket to use this application level protocol
   * @return A pair of test probes, TestPublisher.Probe for sending messages to the endpoint and
   *     TestSubscriber.Probe for expecting messages from the service
   */
  WsConnection<String> wsTextConnection(String path, String protocol);

  /**
   * @param path A path to a web socket endpoint in the service
   * @return A pair of test probes, TestPublisher.Probe for sending messages to the endpoint and
   *     TestSubscriber.Probe for expecting messages from the service
   */
  WsConnection<ByteString> wsBinaryConnection(String path);

  /**
   * @param path A path to a web socket endpoint in the service
   * @param protocol Request the server websocket to use this application level protocol
   * @return A pair of test probes, TestPublisher.Probe for sending messages to the endpoint and
   *     TestSubscriber.Probe for expecting messages from the service
   */
  WsConnection<ByteString> wsBinaryConnection(String path, String protocol);
}
