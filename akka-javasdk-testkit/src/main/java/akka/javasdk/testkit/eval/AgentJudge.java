/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.client.ComponentClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A {@link Judge} that asks a model through {@link JudgeAgent}.
 *
 * <p>The system message is read from {@code eval/judge-prompt.txt} on the classpath. A custom
 * prompt passed to {@link #withPrompt} must ask for the same reply format: a JSON object with a
 * {@code score} between 0 and 1 and a {@code reason}.
 *
 * <p>Each question runs in a new session.
 */
public final class AgentJudge implements Judge {

  static final String PROMPT_RESOURCE = "/eval/judge-prompt.txt";

  private final ComponentClient componentClient;
  private final String prompt;

  AgentJudge(ComponentClient componentClient, String prompt) {
    if (componentClient == null) throw new IllegalArgumentException("componentClient required");
    if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt required");
    this.componentClient = componentClient;
    this.prompt = prompt;
  }

  /** A judge with the default prompt. */
  public static AgentJudge backedBy(ComponentClient componentClient) {
    return new AgentJudge(componentClient, defaultPrompt());
  }

  /** The same judge with another system message. */
  public AgentJudge withPrompt(String prompt) {
    return new AgentJudge(componentClient, prompt);
  }

  public String prompt() {
    return prompt;
  }

  @Override
  public Verdict assess(Question question) {
    return componentClient
        .forAgent()
        .inSession(UUID.randomUUID().toString())
        .method(JudgeAgent::assess)
        .invoke(new JudgeAgent.Request(prompt, material(question)));
  }

  /** The criterion and the evidence as text, in labelled sections. */
  static String material(Question question) {
    var material = new StringBuilder();
    material.append("Criterion:\n").append(question.criterion());
    material.append("\n\nThe user asked:\n").append(question.userMessage());
    material.append("\n\nThe agent replied:\n").append(question.reply());
    if (!question.toolCalls().isEmpty()) {
      material.append("\n\nTools called, in order:");
      for (var call : question.toolCalls()) {
        material.append("\n- ").append(call.name()).append(' ').append(call.arguments());
        call.result().ifPresent(result -> material.append(" -> ").append(result));
        call.error().ifPresent(error -> material.append(" -> failed: ").append(error));
      }
    }
    return material.toString();
  }

  static String defaultPrompt() {
    try (var in = AgentJudge.class.getResourceAsStream(PROMPT_RESOURCE)) {
      if (in == null) throw new IllegalStateException(PROMPT_RESOURCE + " is not on the classpath");
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + PROMPT_RESOURCE, e);
    }
  }
}
