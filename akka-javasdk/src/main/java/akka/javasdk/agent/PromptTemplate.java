/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import java.util.Optional;

/**
 * A built-in Event Sourced Entity for managing dynamic prompt templates with change history.
 *
 * <p>PromptTemplate allows you to change agent prompts at runtime without restarting or redeploying
 * the service. Since it's managed as an entity, you retain full change history and can subscribe to
 * prompt changes.
 *
 * <p><strong>Automatic Registration:</strong> The Akka runtime automatically registers this entity
 * when it detects an {@link Agent} component in your service.
 *
 * <p><strong>Template Parameters:</strong> Templates support Java {@link String#formatted} style
 * parameters when using {@code systemMessageFromTemplate(templateId, args...)}.
 *
 * <p><strong>Change Monitoring:</strong> You can subscribe to prompt template changes using a
 * Consumer to build views or react to prompt updates.
 */
@Component(
    id = "akka-prompt-template",
    name = "Agent Prompt Template",
    description =
        """
        Stores the current prompt template for an agent, including its change history.
        Use this component to view or update the instructions that guide the agent's behavior.
        """)
public final class PromptTemplate
    extends EventSourcedEntity<PromptTemplate.Prompt, PromptTemplate.Event> {

  public record Prompt(String value) { // a wrapper instead of a String to allow further evolution
  }

  public sealed interface Event {
    @TypeName("akka-prompt-updated")
    record Updated(String prompt) implements Event {}

    @TypeName("akka-prompt-deleted")
    record Deleted() implements Event {}
  }

  /**
   * Initialize the prompt template. Call this method for existing prompt template will be ignored,
   * so it's safe to use it e.g. in {@link akka.javasdk.ServiceSetup} to initialize the prompt
   * template with default value.
   */
  public Effect<Done> init(String prompt) {
    if (prompt == null || prompt.isBlank()) {
      return effects().error("Prompt cannot be null or empty");
    } else if (currentState() != null) {
      // ignore if the prompt is already set
      return effects().reply(done());
    } else {
      return effects().persist(new Event.Updated(prompt)).thenReply(__ -> done());
    }
  }

  /**
   * Update the prompt template. Updating the prompt template with the same value will be ignored.
   */
  public Effect<Done> update(String prompt) {
    if (prompt == null || prompt.isBlank()) {
      return effects().error("Prompt cannot be null or empty");
    } else if (currentState() != null && currentState().value().equals(prompt)) {
      // ignore if the prompt is the same
      return effects().reply(done());
    } else {
      return effects().persist(new Event.Updated(prompt)).thenReply(__ -> done());
    }
  }

  /**
   * Delete the prompt template. If the prompt template was already deleted or never set, the call
   * will succeed.
   */
  public Effect<Done> delete() {
    if (currentState() == null) {
      return effects().reply(done());
    } else if (isDeleted()) {
      return effects().reply(done());
    } else {
      return effects().persist(new Event.Deleted()).deleteEntity().thenReply(__ -> done());
    }
  }

  /**
   * Get the prompt template. If the prompt template is not set or deleted, an error will be
   * returned.
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
   * Get the prompt template. If the prompt template is not set or deleted, an empty optional will
   * be returned.
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
