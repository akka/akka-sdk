/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.objectstorage.ObjectStorage;
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
   * Inline content already loaded as bytes.
   *
   * <p>Counterpart to {@link LoadableMessageContent}: where loadable content references its bytes
   * by URI and is resolved by the runtime, {@code DataMessageContent} carries the bytes directly,
   * typically after a {@link ContentLoader} or object-storage resolution has happened.
   *
   * <p>Application code is not expected to construct these directly; reference content by URI (e.g.
   * {@code object://bucket/key}) and let the runtime load it. Concrete implementations are provided
   * internally and produced by the testkit so tests can inspect what the model received.
   */
  sealed interface DataMessageContent extends MessageContent {
    byte[] data();

    Optional<String> mimeType();
  }

  /** Image content carried as inline bytes. See {@link DataMessageContent}. */
  non-sealed interface ImageDataMessageContent extends DataMessageContent {
    ImageMessageContent.DetailLevel detailLevel();
  }

  /** PDF content carried as inline bytes. See {@link DataMessageContent}. */
  non-sealed interface PdfDataMessageContent extends DataMessageContent {}

  /**
   * Image content carried as inline bytes, for returning binary media from a {@code @FunctionTool}.
   *
   * <p>Inline bytes are only valid as a tool return value; they cannot be sent to the model as
   * input. In a {@link UserMessage} reference images by URI using {@link ImageUrlMessageContent}.
   *
   * <p>The {@code data} array must be effectively immutable — do not modify it after passing it in.
   *
   * <p>Build via {@link ImageMessageContent#fromBytes(byte[], String)}.
   */
  record ImageBytesMessageContent(
      byte[] data, Optional<String> mimeType, ImageMessageContent.DetailLevel detailLevel)
      implements ImageDataMessageContent {}

  /**
   * PDF content carried as inline bytes, for returning binary media from a {@code @FunctionTool}.
   *
   * <p>Inline bytes are only valid as a tool return value; they cannot be sent to the model as
   * input. In a {@link UserMessage} reference PDFs by URI using {@link PdfUrlMessageContent}.
   *
   * <p>The {@code data} array must be effectively immutable — do not modify it after passing it in.
   *
   * <p>Build via {@link PdfMessageContent#fromBytes(byte[])}.
   */
  record PdfBytesMessageContent(byte[] data) implements PdfDataMessageContent {

    private static final Optional<String> mimeType = Optional.of("application/pdf");

    @Override
    public Optional<String> mimeType() {
      return mimeType;
    }
  }

  /**
   * Validates that every content item may be sent to the model as input.
   *
   * <p>Inline byte payloads ({@link DataMessageContent}) are only supported as a
   * {@code @FunctionTool} return value; as model input, images and PDFs must be referenced by URI.
   * This is a deliberate, permanent constraint: a user message can travel across the cluster, so it
   * must stay reference-based (a URI) rather than carry potentially large inline bytes.
   *
   * @throws IllegalArgumentException if any item carries inline bytes
   */
  static void requireSendableAsInput(Iterable<? extends MessageContent> contents) {
    for (MessageContent content : contents) {
      if (content instanceof DataMessageContent) {
        throw new IllegalArgumentException(
            "Inline byte content ("
                + content.getClass().getSimpleName()
                + ")"
                + " cannot be sent to the model as input. Reference images/PDFs by URI using"
                + " ImageUrlMessageContent or PdfUrlMessageContent.Inline bytes are only supported"
                + " as a @FunctionTool return value.");
      }
    }
  }

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
     * Creates image content referencing an object in a bucket via the {@code object://} URI scheme.
     *
     * @param bucket the object-storage bucket
     * @param key the object key within the bucket
     */
    public static ImageUrlMessageContent create(ObjectStorage bucket, String key) {
      return new ImageUrlMessageContent(
          URI.create("object://" + bucket.bucketName() + "/" + key),
          ImageMessageContent.DetailLevel.AUTO);
    }

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
    @Deprecated(forRemoval = true, since = "3.5.19")
    public ImageUrlMessageContent(
        URL url, ImageMessageContent.DetailLevel detailLevel, Optional<String> mimeType) {
      this(URI.create(url.toString()), detailLevel, mimeType);
    }

    /**
     * @deprecated Use {@link #ImageUrlMessageContent(URI, ImageMessageContent.DetailLevel)}
     *     instead.
     */
    @Deprecated(forRemoval = true, since = "3.5.19")
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
    @Deprecated(forRemoval = true, since = "3.5.19")
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
     * Creates image content from a URI string with automatic detail level.
     *
     * @param uri The URI string pointing to the image. Supports {@code http(s)://} as well as
     *     custom schemes resolved by a {@link ContentLoader} and {@code object://bucket/key} backed
     *     by object storage.
     * @return A new ImageUrlMessageContent instance with AUTO detail level
     */
    public static ImageUrlMessageContent fromUri(String uri) {
      return new ImageUrlMessageContent(URI.create(uri), DetailLevel.AUTO);
    }

    /**
     * Creates image content from a URI with automatic detail level.
     *
     * @param uri The URI pointing to the image
     * @return A new ImageUrlMessageContent instance with AUTO detail level
     */
    public static ImageUrlMessageContent fromUri(URI uri) {
      return new ImageUrlMessageContent(uri, DetailLevel.AUTO);
    }

    /**
     * Creates image content from a URI with a specific detail level.
     *
     * @param uri The URI pointing to the image
     * @param detailLevel The level of detail for image processing
     * @return A new ImageUrlMessageContent instance
     */
    public static ImageUrlMessageContent fromUri(URI uri, DetailLevel detailLevel) {
      return new ImageUrlMessageContent(uri, detailLevel);
    }

    /**
     * Creates image content from a URI with a specific detail level and explicit mime type.
     *
     * @param uri The URI pointing to the image
     * @param detailLevel The level of detail for image processing
     * @param mimeType The mimeType of the image, e.g. 'image/jpeg', 'image/png'
     * @return A new ImageUrlMessageContent instance
     */
    public static ImageUrlMessageContent fromUri(
        URI uri, DetailLevel detailLevel, String mimeType) {
      return new ImageUrlMessageContent(uri, detailLevel, Optional.of(mimeType));
    }

    /**
     * Creates inline image content from raw bytes, for returning an image from a
     * {@code @FunctionTool}. Not valid as model input — reference images by URI in a {@link
     * UserMessage}.
     *
     * @param data the raw image bytes; must be effectively immutable — do not modify after passing
     *     it in
     * @param mimeType the image media type, e.g. {@code image/png}
     */
    public static ImageBytesMessageContent fromBytes(byte[] data, String mimeType) {
      return new ImageBytesMessageContent(data, Optional.of(mimeType), DetailLevel.AUTO);
    }

    /**
     * Creates inline image content from raw bytes with an explicit detail level, for returning an
     * image from a {@code @FunctionTool}. Not valid as model input.
     *
     * @param data the raw image bytes; must be effectively immutable — do not modify after passing
     *     it in
     * @param mimeType the image media type, e.g. {@code image/png}
     * @param detailLevel the level of detail for image processing
     */
    public static ImageBytesMessageContent fromBytes(
        byte[] data, String mimeType, DetailLevel detailLevel) {
      return new ImageBytesMessageContent(data, Optional.of(mimeType), detailLevel);
    }

    /**
     * @deprecated Use {@link #fromUri(String)} instead.
     */
    @Deprecated(forRemoval = true, since = "3.6.0")
    public static ImageUrlMessageContent fromUrl(String url) {
      return fromUri(url);
    }

    /**
     * @deprecated Use {@link #fromUri(URI)} instead.
     */
    @Deprecated(forRemoval = true, since = "3.6.0")
    public static ImageUrlMessageContent fromUrl(URL url) {
      return fromUri(URI.create(url.toString()));
    }

    /**
     * @deprecated Use {@link #fromUri(URI, DetailLevel)} instead.
     */
    @Deprecated(forRemoval = true, since = "3.6.0")
    public static ImageUrlMessageContent fromUrl(URL url, DetailLevel detailLevel) {
      return fromUri(URI.create(url.toString()), detailLevel);
    }

    /**
     * @deprecated Use {@link #fromUri(URI, DetailLevel, String)} instead.
     */
    @Deprecated(forRemoval = true, since = "3.6.0")
    public static ImageUrlMessageContent fromUrl(
        URL url, DetailLevel detailLevel, String mimeType) {
      return fromUri(URI.create(url.toString()), detailLevel, mimeType);
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
     * Creates PDF content referencing an object in a bucket via the {@code object://} URI scheme.
     *
     * @param bucket the object-storage bucket
     * @param key the object key within the bucket
     */
    public static PdfUrlMessageContent create(ObjectStorage bucket, String key) {
      return new PdfUrlMessageContent(URI.create("object://" + bucket.bucketName() + "/" + key));
    }

    /**
     * @deprecated Use {@link #PdfUrlMessageContent(URI)} instead.
     */
    @Deprecated(forRemoval = true, since = "3.5.19")
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
    @Deprecated(forRemoval = true, since = "3.5.19")
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
     * Creates PDF content from a URI string.
     *
     * @param uri The URI string pointing to the PDF. Supports {@code http(s)://} as well as custom
     *     schemes resolved by a {@link ContentLoader} and {@code object://bucket/key} backed by
     *     object storage.
     * @return A new PdfUrlMessageContent instance
     */
    public static PdfUrlMessageContent fromUri(String uri) {
      return new PdfUrlMessageContent(URI.create(uri));
    }

    /**
     * Creates PDF content from a URI.
     *
     * @param uri The URI pointing to the PDF
     * @return A new PdfUrlMessageContent instance
     */
    public static PdfUrlMessageContent fromUri(URI uri) {
      return new PdfUrlMessageContent(uri);
    }

    /**
     * Creates inline PDF content from raw bytes, for returning a PDF from a {@code @FunctionTool}.
     * Not valid as model input — reference PDFs by URI in a {@link UserMessage}.
     *
     * @param data the raw PDF bytes; must be effectively immutable — do not modify after passing it
     *     in
     */
    public static PdfBytesMessageContent fromBytes(byte[] data) {
      return new PdfBytesMessageContent(data);
    }

    /**
     * @deprecated Use {@link #fromUri(String)} instead.
     */
    @Deprecated(forRemoval = true, since = "3.6.0")
    public static PdfUrlMessageContent fromUrl(String url) {
      return fromUri(url);
    }

    /**
     * @deprecated Use {@link #fromUri(URI)} instead.
     */
    @Deprecated(forRemoval = true, since = "3.6.0")
    public static PdfUrlMessageContent fromUrl(URL url) {
      return fromUri(URI.create(url.toString()));
    }
  }
}
