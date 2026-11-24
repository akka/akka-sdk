/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import java.util.Map;
import java.util.Optional;

/** A part of an A2A message: text, structured data or a file. */
public sealed interface A2aPart {

  /** Plain text. */
  record TextPart(String text) implements A2aPart {}

  /**
   * A JSON object.
   *
   * @param data plain JSON values: strings, numbers, booleans, null, and maps and lists of those
   */
  record DataPart(Map<String, Object> data) implements A2aPart {}

  /**
   * A file, given either by {@code uri} or by base64 encoded {@code bytes}.
   *
   * @param bytes base64 encoded file content
   */
  record FilePart(
      Optional<String> uri,
      Optional<String> bytes,
      Optional<String> mediaType,
      Optional<String> filename)
      implements A2aPart {}
}
