/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One item of a stream of updates, and the body of a push notification. Exactly one member is set.
 * The JSON form is an object with that one member, for example {@code {"statusUpdate": {...}}}.
 *
 * @param task the full task
 * @param message a message
 * @param statusUpdate a change of task status
 * @param artifactUpdate a new or extended artifact
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record A2aStreamResponse(
    A2aTask task,
    A2aMessage message,
    A2aTaskStatusUpdate statusUpdate,
    A2aTaskArtifactUpdate artifactUpdate) {

  public A2aStreamResponse {
    A2aValues.exactlyOne("A2aStreamResponse", task, message, statusUpdate, artifactUpdate);
  }

  public static A2aStreamResponse of(A2aTask task) {
    return new A2aStreamResponse(task, null, null, null);
  }

  public static A2aStreamResponse of(A2aMessage message) {
    return new A2aStreamResponse(null, message, null, null);
  }

  public static A2aStreamResponse of(A2aTaskUpdate update) {
    return switch (update) {
      case A2aTaskStatusUpdate s -> new A2aStreamResponse(null, null, s, null);
      case A2aTaskArtifactUpdate a -> new A2aStreamResponse(null, null, null, a);
    };
  }
}
