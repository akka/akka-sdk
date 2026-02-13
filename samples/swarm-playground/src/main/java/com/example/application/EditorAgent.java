package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "editor-agent")
public class EditorAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
      """
      You are a strict editor. Your job is to polish the provided draft.
      
      - Fix any grammatical errors.
      - Improve flow and readability.
      - Ensure it sounds professional but authentic.
      - Make it punchy.
      """;

  public Effect<String> edit(String draft) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage("Draft to edit:\n" + draft)
        .thenReply();
  }
}
