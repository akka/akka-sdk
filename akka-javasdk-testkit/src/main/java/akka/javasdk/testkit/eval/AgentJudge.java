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
 * <p>The rubric is this kit's, read from {@code eval/judge-prompt.txt} on the classpath; the
 * criterion is the consumer's. A consumer with a rubric of their own hands it to {@link
 * #withPrompt}, keeping the reply contract the prompt states, since the verdict is read back as
 * JSON with a score and a reason.
 *
 * <pre>{@code
 * var judge = Judge.agent(testKit);
 *
 * Expectations.expect()
 *     .tools("getCustomer")
 *     .satisfies(judge.mustSatisfy("the reply states the customer's tier and invents nothing"));
 * }</pre>
 *
 * <p>Every question goes to the agent in a session of its own.
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

  /** The judge with this kit's rubric. */
  public static AgentJudge backedBy(ComponentClient componentClient) {
    return new AgentJudge(componentClient, defaultPrompt());
  }

  /** The same judge with another rubric as the system message. */
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

  /** The evidence, labelled, so the model reads the reply apart from what produced it. */
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
