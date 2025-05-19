/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

import java.util.Optional;

import static akka.Done.done;

@ComponentId("akka-prompt-template")
public class PromptTemplate extends EventSourcedEntity<PromptTemplate.Prompt, PromptTemplate.Event> {

  public record Prompt(String value) { //a wrapper instead of a String to allow further evolution
  }

  public sealed interface Event {
    @TypeName("updated")
    record Updated(String prompt) implements Event {
    }

    @TypeName("deleted")
    record Deleted() implements Event {
    }
  }

  public Effect<Done> init(String prompt) {
    if (prompt == null || prompt.isBlank()) {
      return effects().error("Prompt cannot be null or empty");
    } else if (currentState() != null) {
      //ignore if the prompt is already set
      return effects().reply(done());
    } else {
      return effects()
        .persist(new Event.Updated(prompt))
        .thenReply(__ -> done());
    }
  }

  public Effect<Done> update(String prompt) {
    if (prompt == null || prompt.isBlank()) {
      return effects().error("Prompt cannot be null or empty");
    } else if (currentState() != null && currentState().value().equals(prompt)) {
      //ignore if the prompt is the same
      return effects().reply(done());
    } else {
      return effects()
        .persist(new Event.Updated(prompt))
        .thenReply(__ -> done());
    }
  }

  public Effect<Done> delete() {
    if (currentState() == null) {
      return effects().error("Prompt is not set");
    } else {
      return effects()
        .persist(new Event.Deleted())
        .deleteEntity()
        .thenReply(__ -> done());
    }
  }

  public Effect<String> get() {
    if (currentState() == null) {
      return effects().error("Prompt is not set");
    } else if (isDeleted()) {
      return effects().error("Prompt is deleted");
    } else {
      return effects().reply(currentState().value());
    }
  }

  public Effect<Optional<String>> getOptional() {
    if (currentState() == null) {
      return effects().reply(Optional.empty());
    } else if (isDeleted()) {
      return effects().reply(Optional.empty());
    } else {
      return effects().reply(Optional.of(currentState().value()));
    }
  }

  @Override
  public Prompt applyEvent(Event event) {
    return switch (event) {
      case Event.Updated updated -> new Prompt(updated.prompt);
      case Event.Deleted __ -> new Prompt("");
    };
  }
}
