/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.objectstorage.ObjectStorage;
import com.fasterxml.jackson.annotation.JsonCreator;
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
   * Image content within a user message, referenced by URL.
   *
   * @param url The URL pointing to the image
   * @param detailLevel The level of detail for image processing
   * @param mimeType The mimeType of the image, e.g. 'image/jpeg', 'image/png'
   * @deprecated Use {@link ImageUriMessageContent} instead.
   */
  @Deprecated(forRemoval = true)
  record ImageUrlMessageContent(
      URL url, ImageMessageContent.DetailLevel detailLevel, Optional<String> mimeType)
      implements LoadableMessageContent {

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

  /**
   * Image content within a user message, referenced by URI.
   *
   * <p>Use {@link ImageMessageContent} factory methods to create instances. Supports both regular
   * URIs (such as {@code https://}) and the {@code object://bucket/key} scheme for content stored
   * in object storage.
   *
   * @param uri The URI pointing to the image
   * @param detailLevel The level of detail for image processing
   * @param mimeType The mimeType of the image, e.g. 'image/jpeg', 'image/png'
   */
  record ImageUriMessageContent(
      URI uri, ImageMessageContent.DetailLevel detailLevel, Optional<String> mimeType)
      implements LoadableMessageContent {

    @JsonCreator
    public ImageUriMessageContent(
        URI uri, ImageMessageContent.DetailLevel detailLevel, Optional<String> mimeType) {
      this.uri = uri;
      this.detailLevel = detailLevel;
      this.mimeType = mimeType;
    }

    /**
     * Image content within a user message, referenced by URI.
     *
     * @param uri The URI pointing to the image
     * @param detailLevel The level of detail for image processing
     */
    public ImageUriMessageContent(URI uri, ImageMessageContent.DetailLevel detailLevel) {
      this(uri, detailLevel, Optional.empty());
    }
  }

  /** Factory methods for creating image message content. */
  record ImageMessageContent() {

    /**
     * Creates image content referencing an object in a bucket via the {@code object://} URI scheme.
     *
     * @param bucket the object-storage bucket
     * @param key the object key within the bucket
     */
    public static ImageUriMessageContent create(ObjectStorage bucket, String key) {
      return new ImageUriMessageContent(
          URI.create("object://" + bucket.bucketName() + "/" + key), DetailLevel.AUTO);
    }

    /**
     * Creates image content from a URI with automatic detail level.
     *
     * @param uri The URI pointing to the image
     * @return A new ImageUriMessageContent instance with AUTO detail level
     */
    public static ImageUriMessageContent fromUri(URI uri) {
      return new ImageUriMessageContent(uri, DetailLevel.AUTO);
    }

    /**
     * Creates image content from a URI with a specific detail level.
     *
     * @param uri The URI pointing to the image
     * @param detailLevel The level of detail for image processing
     * @return A new ImageUriMessageContent instance
     */
    public static ImageUriMessageContent fromUri(URI uri, DetailLevel detailLevel) {
      return new ImageUriMessageContent(uri, detailLevel);
    }

    /**
     * Creates image content from a URI with a specific detail level and MIME type.
     *
     * @param uri The URI pointing to the image
     * @param detailLevel The level of detail for image processing
     * @param mimeType The mimeType of the image, e.g. 'image/jpeg', 'image/png'
     * @return A new ImageUriMessageContent instance
     */
    public static ImageUriMessageContent fromUri(
        URI uri, DetailLevel detailLevel, String mimeType) {
      return new ImageUriMessageContent(uri, detailLevel, Optional.of(mimeType));
    }

    /**
     * Creates image content from a URL string with automatic detail level.
     *
     * @param url The URL string pointing to the image
     * @return A new ImageUrlMessageContent instance with AUTO detail level
     * @deprecated Use {@link #fromUri(URI)} instead.
     */
    @Deprecated(forRemoval = true)
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
     * @deprecated Use {@link #fromUri(URI)} instead.
     */
    @Deprecated(forRemoval = true)
    public static ImageUrlMessageContent fromUrl(URL url) {
      return new ImageUrlMessageContent(url, DetailLevel.AUTO);
    }

    /**
     * Creates image content from a URL with a specific detail level.
     *
     * @param url The URL pointing to the image
     * @param detailLevel The level of detail for image processing
     * @return A new ImageUrlMessageContent instance
     * @deprecated Use {@link #fromUri(URI, DetailLevel)} instead.
     */
    @Deprecated(forRemoval = true)
    public static ImageUrlMessageContent fromUrl(URL url, DetailLevel detailLevel) {
      return new ImageUrlMessageContent(url, detailLevel);
    }

    /**
     * Creates image content from a URL with a specific detail level and MIME type.
     *
     * @param url The URL pointing to the image
     * @param detailLevel The level of detail for image processing
     * @param mimeType The mimeType of the image, e.g. 'image/jpeg', 'image/png'
     * @return A new ImageUrlMessageContent instance
     * @deprecated Use {@link #fromUri(URI, DetailLevel, String)} instead.
     */
    @Deprecated(forRemoval = true)
    public static ImageUrlMessageContent fromUrl(
        URL url, DetailLevel detailLevel, String mimeType) {
      return new ImageUrlMessageContent(url, detailLevel, Optional.of(mimeType));
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
   * PDF content within a user message, referenced by URL.
   *
   * @param url The URL pointing to the PDF
   * @deprecated Use {@link PdfUriMessageContent} instead.
   */
  @Deprecated(forRemoval = true)
  record PdfUrlMessageContent(URL url) implements LoadableMessageContent {}

  /**
   * PDF content within a user message, referenced by URI.
   *
   * <p>Use {@link PdfMessageContent} factory methods to create instances. Supports both regular
   * URIs (such as {@code https://}) and the {@code object://bucket/key} scheme for content stored
   * in object storage.
   *
   * @param uri The URI pointing to the PDF
   */
  record PdfUriMessageContent(URI uri) implements LoadableMessageContent {

    @JsonCreator
    public PdfUriMessageContent(URI uri) {
      this.uri = uri;
    }
  }

  /** Factory methods for creating PDF message content. */
  record PdfMessageContent() {

    /**
     * Creates PDF content referencing an object in a bucket via the {@code object://} URI scheme.
     *
     * @param bucket the object-storage bucket
     * @param key the object key within the bucket
     */
    public static PdfUriMessageContent create(ObjectStorage bucket, String key) {
      return new PdfUriMessageContent(URI.create("object://" + bucket.bucketName() + "/" + key));
    }

    /**
     * Creates PDF content from a URI.
     *
     * @param uri The URI pointing to the PDF
     * @return A new PdfUriMessageContent instance
     */
    public static PdfUriMessageContent fromUri(URI uri) {
      return new PdfUriMessageContent(uri);
    }

    /**
     * Creates PDF content from a URL string.
     *
     * @param url The URL string pointing to the PDF
     * @return A new PdfUrlMessageContent instance
     * @deprecated Use {@link #fromUri(URI)} instead.
     */
    @Deprecated(forRemoval = true)
    public static PdfUrlMessageContent fromUrl(String url) {
      try {
        return PdfMessageContent.fromUrl(URI.create(url).toURL());
      } catch (MalformedURLException e) {
        throw new RuntimeException("Can't transform " + url + " to URL", e);
      }
    }

    /**
     * Creates PDF content from a URL.
     *
     * @param url The URL pointing to the PDF
     * @return A new PdfUrlMessageContent instance
     * @deprecated Use {@link #fromUri(URI)} instead.
     */
    @Deprecated(forRemoval = true)
    public static PdfUrlMessageContent fromUrl(URL url) {
      return new PdfUrlMessageContent(url);
    }
  }
}
