/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

/**
 * Represents a piece of content within a multimodal message to an AI model.
 *
 * <p>Message content can be text, images, or PDFs, allowing agents to send multimodal inputs.
 *
 * @see UserMessage
 */
public sealed interface MessageContent {

  sealed interface LoadableMessageContent extends MessageContent {}

  /**
   * Text content within a user message.
   *
   * @param text The text content
   */
  record TextMessageContent(String text) implements MessageContent {

    /**
     * Creates text content from a string.
     *
     * @param text The text content
     * @return A new TextMessageContent instance
     */
    public static TextMessageContent from(String text) {
      return new TextMessageContent(text);
    }
  }

  /**
   * Image content within a user message, referenced by URI.
   *
   * @param uri The URI pointing to the image
   * @param detailLevel The level of detail for image processing
   * @param mimeType The mimeType of the image, e.g. 'image/jpeg', 'image/png'
   */
  record ImageUrlMessageContent(
      URI uri, ImageMessageContent.DetailLevel detailLevel, Optional<String> mimeType)
      implements LoadableMessageContent {

    /**
     * Image content within a user message, referenced by URI.
     *
     * @param uri The URI pointing to the image
     * @param detailLevel The level of detail for image processing
     */
    public ImageUrlMessageContent(URI uri, ImageMessageContent.DetailLevel detailLevel) {
      this(uri, detailLevel, Optional.empty());
    }

    /**
     * @deprecated Use {@link #ImageUrlMessageContent(URI, ImageMessageContent.DetailLevel,
     *     Optional)} instead.
     */
    @Deprecated(forRemoval = true)
    public ImageUrlMessageContent(
        URL url, ImageMessageContent.DetailLevel detailLevel, Optional<String> mimeType) {
      this(URI.create(url.toString()), detailLevel, mimeType);
    }

    /**
     * @deprecated Use {@link #ImageUrlMessageContent(URI, ImageMessageContent.DetailLevel)}
     *     instead.
     */
    @Deprecated(forRemoval = true)
    public ImageUrlMessageContent(URL url, ImageMessageContent.DetailLevel detailLevel) {
      this(URI.create(url.toString()), detailLevel, Optional.empty());
    }

    /**
     * Returns the URI as a {@link URL} for backwards compatibility.
     *
     * @deprecated Use {@link #uri()} instead.
     * @throws RuntimeException if the URI cannot be converted to a URL (e.g. for {@code object://}
     *     URIs)
     */
    @Deprecated(forRemoval = true)
    public URL url() {
      try {
        return uri.toURL();
      } catch (MalformedURLException e) {
        throw new RuntimeException("Cannot convert URI to URL: " + uri, e);
      }
    }
  }

  /** Factory methods for creating image message content. */
  record ImageMessageContent() {

    /**
     * Creates image content from a URL string with automatic detail level.
     *
     * @param url The URL string pointing to the image
     * @return A new ImageUrlMessageContent instance with AUTO detail level
     */
    public static ImageUrlMessageContent fromUrl(String url) {
      return new ImageUrlMessageContent(URI.create(url), DetailLevel.AUTO);
    }

    /**
     * Creates image content from a URL with automatic detail level.
     *
     * @param url The URL pointing to the image
     * @return A new ImageUrlMessageContent instance with AUTO detail level
     */
    public static ImageUrlMessageContent fromUrl(URL url) {
      return new ImageUrlMessageContent(URI.create(url.toString()), DetailLevel.AUTO);
    }

    /**
     * Creates image content from a URL with a specific detail level.
     *
     * @param url The URL pointing to the image
     * @param detailLevel The level of detail for image processing
     * @return A new ImageUrlMessageContent instance
     */
    public static ImageUrlMessageContent fromUrl(URL url, DetailLevel detailLevel) {
      return new ImageUrlMessageContent(URI.create(url.toString()), detailLevel);
    }

    /**
     * Creates image content from a URL with a specific detail level.
     *
     * @param url The URL pointing to the image
     * @param detailLevel The level of detail for image processing
     * @param mimeType The mimeType of the image, e.g. 'image/jpeg', 'image/png'
     * @return A new ImageUrlMessageContent instance
     */
    public static ImageUrlMessageContent fromUrl(
        URL url, DetailLevel detailLevel, String mimeType) {
      return new ImageUrlMessageContent(
          URI.create(url.toString()), detailLevel, Optional.of(mimeType));
    }

    /**
     * Controls the level of detail used when processing images.
     *
     * <p>The detail level affects both the quality of image analysis and the number of tokens
     * consumed by the AI model.
     *
     * <p>Some models might require additional configuration to actually apply this detail level.
     * For example, when using Gemini, per-part image resolution must be enabled via the {@code
     * akka.javasdk.agent.google-ai-gemini.media-resolution-per-part-enabled} setting.
     */
    public enum DetailLevel {
      /** Lower resolution processing, faster and uses fewer tokens. */
      LOW,
      /** Medium resolution processing, balance between detail, cost, and latency. */
      MEDIUM,
      /** Higher resolution processing, more detailed analysis but uses more tokens. */
      HIGH,
      /** Ultra-high resolution processing, highest token count. */
      ULTRA_HIGH,
      /** Let the model automatically choose the appropriate detail level. */
      AUTO;
    }
  }

  /**
   * PDF content within a user message, referenced by URI.
   *
   * @param uri The URI pointing to the PDF
   */
  record PdfUrlMessageContent(URI uri) implements LoadableMessageContent {

    /**
     * @deprecated Use {@link #PdfUrlMessageContent(URI)} instead.
     */
    @Deprecated(forRemoval = true)
    public PdfUrlMessageContent(URL url) {
      this(URI.create(url.toString()));
    }

    /**
     * Returns the URI as a {@link URL} for backwards compatibility.
     *
     * @deprecated Use {@link #uri()} instead.
     * @throws RuntimeException if the URI cannot be converted to a URL (e.g. for {@code object://}
     *     URIs)
     */
    @Deprecated(forRemoval = true)
    public URL url() {
      try {
        return uri.toURL();
      } catch (MalformedURLException e) {
        throw new RuntimeException("Cannot convert URI to URL: " + uri, e);
      }
    }
  }

  /** Factory methods for creating PDF message content. */
  record PdfMessageContent() {

    /**
     * Creates PDF content from a URL string.
     *
     * @param url The URL string pointing to the PDF
     * @return A new PdfUrlMessageContent instance
     */
    public static PdfUrlMessageContent fromUrl(String url) {
      return new PdfUrlMessageContent(URI.create(url));
    }

    /**
     * Creates PDF content from a URL.
     *
     * @param url The URL pointing to the PDF
     * @return A new PdfUrlMessageContent instance
     */
    public static PdfUrlMessageContent fromUrl(URL url) {
      return new PdfUrlMessageContent(URI.create(url.toString()));
    }
  }
}
