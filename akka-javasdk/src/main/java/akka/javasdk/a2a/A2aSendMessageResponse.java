/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.javasdk.impl.a2a.A2aUnions;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Objects;

/**
 * The result of {@code SendMessage}: a {@link Task}, or a {@link Message} when the agent answers
 * without creating a task. The JSON form is an object with one member, {@code {"task": {...}}} or
 * {@code {"message": {...}}}.
 */
@JsonDeserialize(using = A2aUnions.SendMessageResponseDeserializer.class)
public sealed interface A2aSendMessageResponse
    permits A2aSendMessageResponse.Task, A2aSendMessageResponse.Message {

  static A2aSendMessageResponse of(A2aTask task) {
    return new Task(task);
  }

  static A2aSendMessageResponse of(A2aMessage message) {
    return new Message(message);
  }

  /** The agent answered with a task. */
  @JsonDeserialize(using = JsonDeserializer.None.class)
  record Task(A2aTask task) implements A2aSendMessageResponse {
    public Task {
      Objects.requireNonNull(task, "task");
    }
  }

  /** The agent answered with a message and created no task. */
  @JsonDeserialize(using = JsonDeserializer.None.class)
  record Message(A2aMessage message) implements A2aSendMessageResponse {
    public Message {
      Objects.requireNonNull(message, "message");
    }
  }
}
