/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The result of {@code SendMessage}: a task, or a message when the agent answers without creating a
 * task. Exactly one member is set.
 *
 * @param task the task
 * @param message the message
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record A2aSendMessageResponse(A2aTask task, A2aMessage message) {

  public A2aSendMessageResponse {
    A2aValues.exactlyOne("A2aSendMessageResponse", task, message);
  }

  public static A2aSendMessageResponse of(A2aTask task) {
    return new A2aSendMessageResponse(task, null);
  }

  public static A2aSendMessageResponse of(A2aMessage message) {
    return new A2aSendMessageResponse(null, message);
  }
}
