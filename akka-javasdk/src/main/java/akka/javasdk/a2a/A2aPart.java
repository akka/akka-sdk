/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.javasdk.impl.a2a.A2aUnions;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * One piece of content in a message or an artifact: {@link Text}, {@link Raw}, {@link Url} or
 * {@link Data}.
 *
 * <p>The JSON form is flat, for example {@code {"text": "hello"}} or {@code {"url": "https://...",
 * "mediaType": "image/png"}}. Every kind of part may carry a media type, a file name and metadata.
 */
@JsonDeserialize(using = A2aUnions.PartDeserializer.class)
public sealed interface A2aPart permits A2aPart.Text, A2aPart.Raw, A2aPart.Url, A2aPart.Data {

  /** The MIME type of the content, for example {@code image/png}, or null. */
  String mediaType();

  /** A name for the content, or null. */
  String filename();

  /** Free form details about this part, or null. */
  Map<String, Object> metadata();

  /** A text part. */
  static Text text(String text) {
    return new Text(text, null, null, null);
  }

  /** A binary part. */
  static Raw raw(byte[] bytes, String mediaType, String filename) {
    return new Raw(Base64.getEncoder().encodeToString(bytes), mediaType, filename, null);
  }

  /** A part that links to its content. */
  static Url url(String url, String mediaType, String filename) {
    return new Url(url, mediaType, filename, null);
  }

  /** A structured data part. */
  static Data data(Object data) {
    return new Data(data, null, null, null);
  }

  /**
   * Plain text content.
   *
   * @param text the text, which may be empty
   * @param mediaType the MIME type, or null
   * @param filename a name for the content, or null
   * @param metadata free form details, or null
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonDeserialize(using = JsonDeserializer.None.class)
  record Text(String text, String mediaType, String filename, Map<String, Object> metadata)
      implements A2aPart {
    public Text {
      Objects.requireNonNull(text, "A text part needs its text");
      metadata = A2aValues.optionalMap(metadata);
    }
  }

  /**
   * Binary content.
   *
   * @param raw the bytes as a Base64 string, as it appears in JSON; use {@link #rawBytes()} for the
   *     bytes
   * @param mediaType the MIME type, or null
   * @param filename a name for the content, or null
   * @param metadata free form details, or null
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonDeserialize(using = JsonDeserializer.None.class)
  record Raw(String raw, String mediaType, String filename, Map<String, Object> metadata)
      implements A2aPart {
    public Raw {
      Objects.requireNonNull(raw, "A raw part needs its content");
      decodeBase64(raw);
      metadata = A2aValues.optionalMap(metadata);
    }

    /** The decoded bytes. */
    @JsonIgnore
    public byte[] rawBytes() {
      return decodeBase64(raw);
    }

    // The protocol's JSON mapping accepts both the standard and the URL safe Base64 alphabet.
    private static byte[] decodeBase64(String value) {
      try {
        return Base64.getDecoder().decode(value.replace('-', '+').replace('_', '/'));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("A2aPart raw content is not valid Base64", e);
      }
    }
  }

  /**
   * A link to the content.
   *
   * @param url the link
   * @param mediaType the MIME type, or null
   * @param filename a name for the content, or null
   * @param metadata free form details, or null
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonDeserialize(using = JsonDeserializer.None.class)
  record Url(String url, String mediaType, String filename, Map<String, Object> metadata)
      implements A2aPart {
    public Url {
      Objects.requireNonNull(url, "A url part needs its url");
      metadata = A2aValues.optionalMap(metadata);
    }
  }

  /**
   * Structured content.
   *
   * @param data any JSON value except null, read as a map, list, string, number or boolean
   * @param mediaType the MIME type, or null
   * @param filename a name for the content, or null
   * @param metadata free form details, or null
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonDeserialize(using = JsonDeserializer.None.class)
  record Data(Object data, String mediaType, String filename, Map<String, Object> metadata)
      implements A2aPart {
    public Data {
      Objects.requireNonNull(data, "A data part needs its data");
      metadata = A2aValues.optionalMap(metadata);
    }
  }
}
