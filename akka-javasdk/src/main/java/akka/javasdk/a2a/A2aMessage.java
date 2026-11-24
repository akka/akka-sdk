/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A message from an A2A caller.
 *
 * @param messageId set by the caller
 * @param contextId the context id of the conversation. The caller sets it to group the messages of
 *     a conversation. The runtime generates one for the first message, and sends it to the caller
 *     in the reply.
 * @param metadata plain JSON values: strings, numbers, booleans, null, and maps and lists of those
 */
public record A2aMessage(
    String messageId, String contextId, List<A2aPart> parts, Map<String, Object> metadata) {

  /**
   * @return The text of all text parts, joined with new lines. Empty if the message has no text
   *     part.
   */
  public String text() {
    return parts.stream()
        .map(A2aPart::content)
        .filter(content -> content instanceof A2aPart.Content.Text)
        .map(content -> ((A2aPart.Content.Text) content).text())
        .collect(Collectors.joining("\n"));
  }
}
