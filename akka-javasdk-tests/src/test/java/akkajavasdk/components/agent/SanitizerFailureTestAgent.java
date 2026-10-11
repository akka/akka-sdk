/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.SanitizerException;
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import java.util.Objects;

/** Bound by component id to the "failing" sanitizer, see test application.conf. */
@Component(id = "sanitizer-failure-test-agent")
public class SanitizerFailureTestAgent extends Agent {
  public record SomeResponse(String response, String sanitizerName, String cause) {}

  public Effect<SomeResponse> ask(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .map(reply -> new SomeResponse(reply, null, null))
        .onFailure(
            cause -> {
              return switch (cause) {
                case SanitizerException e ->
                    new SomeResponse(
                        e.getMessage(), e.getSanitizerName(), Objects.toString(e.getCause(), null));
                case RuntimeException e -> throw e;
                default -> throw new RuntimeException(cause);
              };
            })
        .thenReply();
  }

  @FunctionTool(description = "Returns the notes for a customer")
  private String getNotes(String customer) {
    return "Notes for " + customer + ": failsanitizer";
  }
}
