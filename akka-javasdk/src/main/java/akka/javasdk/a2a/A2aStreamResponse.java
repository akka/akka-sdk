/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.javasdk.impl.a2a.A2aUnions;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Objects;

/**
 * One item of a stream of updates, and the body of a push notification: a {@link Task}, a {@link
 * Message}, a {@link StatusUpdate} or an {@link ArtifactUpdate}. The JSON form is an object with
 * one member, for example {@code {"statusUpdate": {...}}}.
 */
@JsonDeserialize(using = A2aUnions.StreamResponseDeserializer.class)
public sealed interface A2aStreamResponse
    permits A2aStreamResponse.Task,
        A2aStreamResponse.Message,
        A2aStreamResponse.StatusUpdate,
        A2aStreamResponse.ArtifactUpdate {

  static A2aStreamResponse of(A2aTask task) {
    return new Task(task);
  }

  static A2aStreamResponse of(A2aMessage message) {
    return new Message(message);
  }

  static A2aStreamResponse of(A2aTaskUpdate update) {
    return switch (update) {
      case A2aTaskStatusUpdate s -> new StatusUpdate(s);
      case A2aTaskArtifactUpdate a -> new ArtifactUpdate(a);
    };
  }

  /** The full task. */
  @JsonDeserialize(using = JsonDeserializer.None.class)
  record Task(A2aTask task) implements A2aStreamResponse {
    public Task {
      Objects.requireNonNull(task, "task");
    }
  }

  /** A message. */
  @JsonDeserialize(using = JsonDeserializer.None.class)
  record Message(A2aMessage message) implements A2aStreamResponse {
    public Message {
      Objects.requireNonNull(message, "message");
    }
  }

  /** A change of task status. */
  @JsonDeserialize(using = JsonDeserializer.None.class)
  record StatusUpdate(A2aTaskStatusUpdate statusUpdate) implements A2aStreamResponse {
    public StatusUpdate {
      Objects.requireNonNull(statusUpdate, "statusUpdate");
    }
  }

  /** A new or extended artifact. */
  @JsonDeserialize(using = JsonDeserializer.None.class)
  record ArtifactUpdate(A2aTaskArtifactUpdate artifactUpdate) implements A2aStreamResponse {
    public ArtifactUpdate {
      Objects.requireNonNull(artifactUpdate, "artifactUpdate");
    }
  }
}
