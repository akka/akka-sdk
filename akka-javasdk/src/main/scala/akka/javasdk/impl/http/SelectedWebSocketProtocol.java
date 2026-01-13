/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http;

import akka.NotUsed;
import akka.stream.javadsl.Flow;

public record SelectedWebSocketProtocol<T>(String protocolName, Flow<T, T, NotUsed> handler) {
}
