/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.Optional;

/**
 * Interface for loading content from URIs.
 *
 * <p>Implement this interface to provide custom content loading logic for {@link
 * akka.javasdk.agent.MessageContent.LoadableMessageContent} types. This is useful when content must
 * be fetched from custom sources such as cloud storage, databases, or authenticated endpoints.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class MyContentLoader implements ContentLoader {
 *   @Override
 *   public LoadedContent load(MessageContent.LoadableMessageContent content) {
 *     return switch (content) {
 *       case MessageContent.ImageUrlMessageContent image -> {
 *         byte[] data = fetchFromStorage(image.url());
 *         String mimeType = image.mimeType().orElse("image/jpeg");
 *         yield new LoadedContent(data, Optional.of(mimeType));
 *       }
 *       case MessageContent.PdfUrlMessageContent pdf -> {
 *         byte[] data = fetchFromStorage(pdf.url());
 *         yield new LoadedContent(data);
 *       }
 *     };
 *   }
 * }
 * }</pre>
 *
 * <p>To use the content loader, pass it to the agent effect builder:
 *
 * <pre>{@code
 * return effects()
 *     .contentLoader(new MyContentLoader())
 *     .userMessage(UserMessage.from(
 *         MessageContent.TextMessageContent.from("Describe this image"),
 *         MessageContent.ImageMessageContent.fromUrl(imageUrl)))
 *     .thenReply();
 * }</pre>
 *
 * <p>The instance used could be a new one for each agent request, to for example allow per-request
 * credentials, or it could be created globally in the service bootstrap, and made available to each
 * agent via dependency injection.
 *
 * <p>In case of a shared instance, care must be taken that it is thread safe since it can be used
 * by multiple separate agent interactions concurrently.
 *
 * @see Agent.Effect.Builder#contentLoader(ContentLoader)
 */
public interface ContentLoader {

  /**
   * Represents loaded content with its binary data and MIME type.
   *
   * @param data The raw content bytes (e.g., image or PDF data)
   * @param mimeType The MIME type of the content (e.g., "image/jpeg", "image/png",
   *     "application/pdf")
   */
  record LoadedContent(byte[] data, Optional<String> mimeType) {

    public static LoadedContent from(byte[] data) {
      return new LoadedContent(data, Optional.empty());
    }
  }

  /**
   * Loads content from the given loadable message content.
   *
   * <p>This method is called by the runtime when processing multimodal messages that contain
   * URL-referenced content (images or PDFs). The implementation should fetch the content data and
   * return it along with the appropriate MIME type.
   *
   * <p>Use pattern matching on the content parameter to handle different content types:
   *
   * <ul>
   *   <li>{@link MessageContent.ImageUrlMessageContent} — provides the URL, detail level, and
   *       optional MIME type hint
   *   <li>{@link MessageContent.PdfUrlMessageContent} — provides the URL of the PDF
   * </ul>
   *
   * <p>If the method throws, the entire agent request is failed.
   *
   * @param content The loadable message content containing the URL and metadata
   * @return The loaded content data and MIME type
   */
  LoadedContent load(MessageContent.LoadableMessageContent content);
}
