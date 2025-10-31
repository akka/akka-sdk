/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.net.URI;

public sealed interface MessageContent {

  record TextMessageContent(String text) implements MessageContent {

    public static TextMessageContent from(String text) {
      return new TextMessageContent(text);
    }
  }

  record ImageMessageContent(URI uri, byte[] bytes, String mimeType, DetailLevel detailLevel)
      implements MessageContent {

    public static ImageMessageContent from(URI uri) {
      return new ImageMessageContent(uri, null, null, DetailLevel.AUTO);
    }

    public static ImageMessageContent from(URI uri, DetailLevel detailLevel) {
      return new ImageMessageContent(uri, null, null, detailLevel);
    }

    // TODO add more

    public enum DetailLevel {
      LOW,
      HIGH,
      AUTO;
    }
  }
}
