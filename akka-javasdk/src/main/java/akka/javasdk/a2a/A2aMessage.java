/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A message from an A2A caller.
 *
 * @param messageId set by the caller
 * @param contextId set by the caller to group the messages of a conversation, empty for the first
 *     message. {@link A2aRequestContext#contextId()} has the context id to use for the call.
 * @param metadata plain JSON values: strings, numbers, booleans, null, and maps and lists of those
 */
public record A2aMessage(
    String messageId,
    Optional<String> contextId,
    List<A2aPart> parts,
    Map<String, Object> metadata) {

  /**
   * @return The text of all text parts, joined with new lines. Empty if the message has no text
   *     part.
   */
  public String text() {
    return parts.stream()
        .filter(part -> part instanceof A2aPart.TextPart)
        .map(part -> ((A2aPart.TextPart) part).text())
        .collect(Collectors.joining("\n"));
  }
}
