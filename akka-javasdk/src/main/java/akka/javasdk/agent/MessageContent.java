/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

/**
 * Represents a piece of content within a multimodal message to an AI model.
 *
 * <p>Message content can be text or images, allowing agents to send multimodal inputs.
 *
 * @see UserMessage
 */
public sealed interface MessageContent {

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
   * Image content within a user message, referenced by URL.
   *
   * @param url The URL pointing to the image
   * @param detailLevel The level of detail for image processing
   * @param mimeType The mimeType of the image, e.g. 'image/jpeg', 'image/png'
   */
  record ImageUrlMessageContent(
      URL url, ImageMessageContent.DetailLevel detailLevel, Optional<String> mimeType)
      implements MessageContent {

    /**
     * Image content within a user message, referenced by URL.
     *
     * @param url The URL pointing to the image
     * @param detailLevel The level of detail for image processing
     */
    public ImageUrlMessageContent(URL url, ImageMessageContent.DetailLevel detailLevel) {
      this(url, detailLevel, Optional.empty());
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
      try {
        return ImageMessageContent.fromUrl(URI.create(url).toURL());
      } catch (MalformedURLException e) {
        throw new RuntimeException("Can't transform " + url + " to URL", e);
      }
    }

    /**
     * Creates image content from a URL with automatic detail level.
     *
     * @param url The URL pointing to the image
     * @return A new ImageUrlMessageContent instance with AUTO detail level
     */
    public static ImageUrlMessageContent fromUrl(URL url) {
      return new ImageUrlMessageContent(url, DetailLevel.AUTO);
    }

    /**
     * Creates image content from a URL with a specific detail level.
     *
     * @param url The URL pointing to the image
     * @param detailLevel The level of detail for image processing
     * @return A new ImageUrlMessageContent instance
     */
    public static ImageUrlMessageContent fromUrl(URL url, DetailLevel detailLevel) {
      return new ImageUrlMessageContent(url, detailLevel);
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
      return new ImageUrlMessageContent(url, detailLevel, Optional.of(mimeType));
    }

    /**
     * Controls the level of detail used when processing images.
     *
     * <p>The detail level affects both the quality of image analysis and the number of tokens
     * consumed by the AI model.
     */
    public enum DetailLevel {
      /** Lower resolution processing, faster and uses fewer tokens */
      LOW,
      /** Higher resolution processing, more detailed analysis but uses more tokens */
      HIGH,
      /** Let the model automatically choose the appropriate detail level */
      AUTO;
    }
  }
}
