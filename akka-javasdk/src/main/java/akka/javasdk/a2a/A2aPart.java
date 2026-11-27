/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import java.util.Map;
import java.util.Optional;

/**
 * A part of an A2A message. A part has content of one kind: text, data, or a file.
 *
 * @param mediaType the media type of the content, if known
 * @param filename the name of the file, for a file part
 * @param metadata plain JSON values: strings, numbers, booleans, null, and maps and lists of those
 */
public record A2aPart(
    Content content,
    Optional<String> mediaType,
    Optional<String> filename,
    Map<String, Object> metadata) {

  /** The content of a part. */
  public sealed interface Content {

    /** Plain text. */
    record Text(String text) implements Content {}

    /**
     * Any JSON value: a string, number, boolean, null, or a map or list of those.
     *
     * @param value plain JSON values
     */
    record Data(Object value) implements Content {}

    /** A file, given by URL. */
    record Url(String url) implements Content {}

    /**
     * A file, given by its content.
     *
     * @param base64 the base64 encoded content of the file
     */
    record Raw(String base64) implements Content {}
  }

  /** A part with text. */
  public static A2aPart text(String text) {
    return of(new Content.Text(text));
  }

  /**
   * A part with a JSON value.
   *
   * @param value plain JSON values: strings, numbers, booleans, null, and maps and lists of those
   */
  public static A2aPart data(Object value) {
    return of(new Content.Data(value));
  }

  /** A part with a file, given by URL. */
  public static A2aPart url(String url) {
    return of(new Content.Url(url));
  }

  /** A part with a file, given by base64 encoded content. */
  public static A2aPart raw(String base64) {
    return of(new Content.Raw(base64));
  }

  /** A part with the content, no media type, no file name and no metadata. */
  public static A2aPart of(Content content) {
    return new A2aPart(content, Optional.empty(), Optional.empty(), Map.of());
  }

  /** A copy of this part with the media type. */
  public A2aPart withMediaType(String mediaType) {
    return new A2aPart(content, Optional.of(mediaType), filename, metadata);
  }

  /** A copy of this part with the file name. */
  public A2aPart withFilename(String filename) {
    return new A2aPart(content, mediaType, Optional.of(filename), metadata);
  }

  /** A copy of this part with the metadata. */
  public A2aPart withMetadata(Map<String, Object> metadata) {
    return new A2aPart(content, mediaType, filename, metadata);
  }
}
