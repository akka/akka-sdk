/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.net.URI;

// TODO merge with MessageContent or keep them separate?
public sealed interface ResponseContent {

  record TextResponseContent(String text) implements ResponseContent {}

  record ImageResponseContent(URI uri, byte[] bytes, String mimeType) implements ResponseContent {}
}
