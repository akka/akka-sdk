/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Base64;
import java.util.Map;

/**
 * One piece of content in a message or an artifact.
 *
 * <p>Exactly one of {@code text}, {@code raw}, {@code url} and {@code data} is set. The other
 * fields describe that content and may always be set. The JSON form is flat, for example {@code
 * {"text": "hello"}} or {@code {"url": "https://...", "mediaType": "image/png"}}.
 *
 * @param text plain text content
 * @param raw binary content as a Base64 string, as it appears in JSON; use {@link #rawBytes()} for
 *     the bytes
 * @param url a link to the content
 * @param data structured content: any JSON value, read as a map, list, string, number or boolean
 * @param metadata free form details about this part
 * @param filename a name for the content, for example for {@code raw} or {@code url} parts
 * @param mediaType the MIME type of the content, for example {@code image/png}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record A2aPart(
    String text,
    String raw,
    String url,
    Object data,
    Map<String, Object> metadata,
    String filename,
    String mediaType) {

  public A2aPart {
    A2aValues.exactlyOne("A2aPart", text, raw, url, data);
    if (raw != null) decodeBase64(raw);
    metadata = A2aValues.optionalMap(metadata);
  }

  /** A text part. */
  public static A2aPart text(String text) {
    return new A2aPart(text, null, null, null, null, null, null);
  }

  /** A binary part. */
  public static A2aPart raw(byte[] bytes, String mediaType, String filename) {
    return new A2aPart(
        null, Base64.getEncoder().encodeToString(bytes), null, null, null, filename, mediaType);
  }

  /** A part that links to its content. */
  public static A2aPart url(String url, String mediaType, String filename) {
    return new A2aPart(null, null, url, null, null, filename, mediaType);
  }

  /** A structured data part. */
  public static A2aPart data(Object data) {
    return new A2aPart(null, null, null, data, null, null, null);
  }

  /** The decoded bytes of a {@code raw} part. */
  public byte[] rawBytes() {
    if (raw == null) throw new IllegalStateException("This part has no raw content");
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
