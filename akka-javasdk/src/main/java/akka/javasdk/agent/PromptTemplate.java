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

/**
 * Prompt template is an ordinary event sourced entity that stores a prompt template with the history of updates.
 * <p>
 * Akka runtime will automatically register this entity when it detects an {@link Agent} component.
 * <p>
 * Use {@link akka.javasdk.client.ComponentClient} to:
 * - initialize the prompt template
 * - update the prompt template
 * - delete the prompt template
 * - get the prompt template
 */
@ComponentId("akka-prompt-template")
public final class PromptTemplate extends EventSourcedEntity<PromptTemplate.Prompt, PromptTemplate.Event> {

  public record Prompt(String value) { //a wrapper instead of a String to allow further evolution
  }

  public sealed interface Event {
    @TypeName("akka-prompt-updated")
    record Updated(String prompt) implements Event {
    }

    @TypeName("akka-prompt-deleted")
    record Deleted() implements Event {
    }
  }

  /**
   * Initialize the prompt template. Call this method for existing prompt template will be ignored, so it's safe to
   * use it e.g. in {@link akka.javasdk.ServiceSetup} to initialize the prompt template with default value.
   */
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

  /**
   * Update the prompt template. Updating the prompt template with the same value will be ignored.
   */
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

  /**
   * Delete the prompt template. If the prompt template was never set, an error will be returned.
   * If the prompt template was already deleted the call will succeed.
   */
  public Effect<Done> delete() {
    if (currentState() == null) {
      return effects().error("Prompt is not set");
    } else if (isDeleted()) {
      return effects().reply(done());
    } else {
      return effects()
        .persist(new Event.Deleted())
        .deleteEntity()
        .thenReply(__ -> done());
    }
  }

  /**
   * Get the prompt template. If the prompt template is not set or deleted, an error will be returned.
   */
  public Effect<String> get() {
    if (currentState() == null) {
      return effects().error("Prompt is not set");
    } else if (isDeleted()) {
      return effects().error("Prompt is deleted");
    } else {
      return effects().reply(currentState().value());
    }
  }

  /**
   * Get the prompt template. If the prompt template is not set or deleted, an empty optional will be returned.
   */
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
