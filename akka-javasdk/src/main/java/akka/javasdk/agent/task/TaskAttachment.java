/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.MessageContent.ImageMessageContent;
import akka.javasdk.agent.MessageContent.ImageUrlMessageContent;
import akka.javasdk.agent.MessageContent.PdfUrlMessageContent;
import akka.javasdk.agent.MessageContent.TextMessageContent;
import java.net.URI;
import java.util.Optional;

/**
 * A serializable reference to content attached to a task. Stores type metadata and either a URI or
 * inline text so that content references survive event sourcing. Reconstructed to {@link
 * MessageContent} at execution time.
 */
public record TaskAttachment(
    String type, URI uri, String text, String detailLevel, String mimeType) {

  /** Convert a {@link MessageContent} to a serializable {@code TaskAttachment}. */
  public static TaskAttachment fromMessageContent(MessageContent content) {
    return switch (content) {
      case TextMessageContent t -> new TaskAttachment("text", null, t.text(), null, null);
      case ImageUrlMessageContent img ->
          new TaskAttachment(
              "image", img.uri(), null, img.detailLevel().name(), img.mimeType().orElse(null));
      case PdfUrlMessageContent pdf -> new TaskAttachment("pdf", pdf.uri(), null, null, null);
      default ->
          throw new IllegalArgumentException("Unsupported content type: " + content.getClass());
    };
  }

  /** Reconstruct the {@link MessageContent} from this reference. */
  public MessageContent toMessageContent() {
    return switch (type) {
      case "text" -> TextMessageContent.from(text);
      case "image" -> {
        var detail =
            detailLevel != null
                ? ImageMessageContent.DetailLevel.valueOf(detailLevel)
                : ImageMessageContent.DetailLevel.AUTO;
        yield mimeType != null
            ? new ImageUrlMessageContent(uri, detail, Optional.of(mimeType))
            : new ImageUrlMessageContent(uri, detail);
      }
      case "pdf" -> new PdfUrlMessageContent(uri);
      default -> throw new IllegalArgumentException("Unknown content type: " + type);
    };
  }
}
