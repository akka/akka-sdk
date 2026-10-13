/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.http.javadsl.model.HttpHeader;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.util.ByteString;
import java.util.List;

public interface WebSocketRouteTester {

  record WsConnection<T>(TestPublisher.Probe<T> publisher, TestSubscriber.Probe<T> subscriber) {}

  /**
   * @param path A path to a web socket endpoint in the service
   * @return A pair of test probes, TestPublisher.Probe for sending messages to the endpoint and
   *     TestSubscriber.Probe for expecting messages from the service
   * @throws WebSocketUpgradeFailedException if the service rejects the upgrade request
   */
  WsConnection<String> wsTextConnection(String path);

  /**
   * @param path A path to a web socket endpoint in the service
   * @param headers HTTP headers to include in the upgrade request, for example an {@code
   *     Authorization} header
   * @return A pair of test probes, TestPublisher.Probe for sending messages to the endpoint and
   *     TestSubscriber.Probe for expecting messages from the service
   * @throws WebSocketUpgradeFailedException if the service rejects the upgrade request
   */
  WsConnection<String> wsTextConnection(String path, List<HttpHeader> headers);

  /**
   * @param path A path to a web socket endpoint in the service
   * @return A pair of test probes, TestPublisher.Probe for sending messages to the endpoint and
   *     TestSubscriber.Probe for expecting messages from the service
   * @throws WebSocketUpgradeFailedException if the service rejects the upgrade request
   */
  WsConnection<ByteString> wsBinaryConnection(String path);

  /**
   * @param path A path to a web socket endpoint in the service
   * @param headers HTTP headers to include in the upgrade request, for example an {@code
   *     Authorization} header
   * @return A pair of test probes, TestPublisher.Probe for sending messages to the endpoint and
   *     TestSubscriber.Probe for expecting messages from the service
   * @throws WebSocketUpgradeFailedException if the service rejects the upgrade request
   */
  WsConnection<ByteString> wsBinaryConnection(String path, List<HttpHeader> headers);
}
