/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.annotation.InternalApi;
import akka.javasdk.agent.MessageContent;
import java.util.Optional;

/**
 * Internal record implementations of {@link MessageContent.ImageDataMessageContent} and {@link
 * MessageContent.PdfDataMessageContent}.
 *
 * <p>Construction is intentionally restricted to SDK-internal callers (testkit and runtime
 * adapters). Application code should reference content via URI and let the runtime load it.
 */
@InternalApi
public final class DataMessageContentImpl {

  private DataMessageContentImpl() {}

  public record Image(
      byte[] data,
      Optional<String> mimeType,
      MessageContent.ImageMessageContent.DetailLevel detailLevel)
      implements MessageContent.ImageDataMessageContent {}

  public record Pdf(byte[] data, Optional<String> mimeType)
      implements MessageContent.PdfDataMessageContent {}
}
