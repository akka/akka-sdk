/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.MessageContent.ImageMessageContent;
import akka.javasdk.agent.MessageContent.ImageUrlMessageContent;
import akka.javasdk.agent.MessageContent.PdfMessageContent;
import akka.javasdk.agent.MessageContent.PdfUrlMessageContent;
import akka.javasdk.agent.MessageContent.TextMessageContent;

/**
 * A serializable reference to content attached to a task. Stores type metadata and either a URL or
 * inline text so that content references survive event sourcing. Reconstructed to {@link
 * MessageContent} at execution time.
 */
public record ContentRef(
    String type, String url, String text, String detailLevel, String mimeType) {

  /** Convert a {@link MessageContent} to a serializable {@code ContentRef}. */
  public static ContentRef fromMessageContent(MessageContent content) {
    return switch (content) {
      case TextMessageContent t -> new ContentRef("text", null, t.text(), null, null);
      case ImageUrlMessageContent img ->
          new ContentRef(
              "image",
              img.url().toString(),
              null,
              img.detailLevel().name(),
              img.mimeType().orElse(null));
      case PdfUrlMessageContent pdf ->
          new ContentRef("pdf", pdf.url().toString(), null, null, null);
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
        try {
          var imageUrl = java.net.URI.create(url).toURL();
          if (mimeType != null) {
            yield ImageMessageContent.fromUrl(imageUrl, detail, mimeType);
          } else {
            yield ImageMessageContent.fromUrl(imageUrl, detail);
          }
        } catch (java.net.MalformedURLException e) {
          throw new RuntimeException("Can't transform " + url + " to URL", e);
        }
      }
      case "pdf" -> PdfMessageContent.fromUrl(url);
      default -> throw new IllegalArgumentException("Unknown content type: " + type);
    };
  }
}
