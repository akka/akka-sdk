/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.client.ComponentClient;
import java.util.UUID;
import java.util.function.Function;

/**
 * A {@link Judge} that asks a model through {@link JudgeAgent}.
 *
 * <p>The system message is {@link #DEFAULT_SYSTEM_MESSAGE}. A custom one passed to {@link
 * #withSystemMessage} must ask for the same reply format: a JSON object with a {@code score}
 * between 0 and 1 and a {@code reason}.
 *
 * <p>The user message is the {@link Input} rendered as text by {@link #defaultUserMessage}. {@link
 * #withUserMessage} replaces that rendering, for example to write the sections in another language.
 *
 * <p>The model is the one configured in {@code akka.javasdk.agent.model-provider}. {@link
 * #withModel} names another configuration path. A provider registered for {@link JudgeAgent} in the
 * TestKit settings takes precedence over both.
 *
 * <p>Each assessment runs in a new session.
 */
public final class AgentJudge implements Judge {

  /** The system message a judge sends unless {@link #withSystemMessage} replaces it. */
  public static final String DEFAULT_SYSTEM_MESSAGE =
      """
      You judge whether an agent's reply meets a stated criterion.

      You will be given the criterion, the message the user sent, the reply the agent produced,
      and the tools it called to produce that reply. Score how fully the reply meets the
      criterion, between 0 (does not meet it) and 1 (fully meets it). Judge the criterion you
      were given and nothing else: a reply you would have worded differently still meets a
      criterion it satisfies.

      Reply as JSON: {"score": <number between 0 and 1>, "reason": "<one sentence>"}
      """;

  private final ComponentClient componentClient;
  private final String systemMessage;
  private final Function<Input, String> userMessage;
  private final String modelConfigPath;

  AgentJudge(
      ComponentClient componentClient,
      String systemMessage,
      Function<Input, String> userMessage,
      String modelConfigPath) {
    if (componentClient == null) throw new IllegalArgumentException("componentClient required");
    if (systemMessage == null || systemMessage.isBlank())
      throw new IllegalArgumentException("systemMessage required");
    if (userMessage == null) throw new IllegalArgumentException("userMessage required");
    if (modelConfigPath == null) throw new IllegalArgumentException("modelConfigPath required");
    this.componentClient = componentClient;
    this.systemMessage = systemMessage;
    this.userMessage = userMessage;
    this.modelConfigPath = modelConfigPath;
  }

  /** A judge with the default system message, the default user message and the default model. */
  public static AgentJudge backedBy(ComponentClient componentClient) {
    return new AgentJudge(
        componentClient, DEFAULT_SYSTEM_MESSAGE, AgentJudge::defaultUserMessage, "");
  }

  /** The same judge with another system message. */
  public AgentJudge withSystemMessage(String systemMessage) {
    return new AgentJudge(componentClient, systemMessage, userMessage, modelConfigPath);
  }

  /**
   * The same judge with another rendering of the input into the user message. The rendering must
   * carry what the system message asks the model to judge.
   */
  public AgentJudge withUserMessage(Function<Input, String> userMessage) {
    return new AgentJudge(componentClient, systemMessage, userMessage, modelConfigPath);
  }

  /**
   * The same judge asking the model configured at this path, for example {@code
   * "eval.judge-model"}, in the same form as {@code akka.javasdk.agent.model-provider}.
   */
  public AgentJudge withModel(String modelConfigPath) {
    if (modelConfigPath == null || modelConfigPath.isBlank())
      throw new IllegalArgumentException("modelConfigPath required");
    return new AgentJudge(componentClient, systemMessage, userMessage, modelConfigPath);
  }

  /** The system message this judge sends. */
  public String systemMessage() {
    return systemMessage;
  }

  /** The configuration path of the model this judge asks; empty for the default model. */
  public String modelConfigPath() {
    return modelConfigPath;
  }

  @Override
  public Verdict assess(Input input) {
    return componentClient
        .forAgent()
        .inSession(UUID.randomUUID().toString())
        .method(JudgeAgent::assess)
        .invoke(new JudgeAgent.Request(systemMessage, userMessage.apply(input), modelConfigPath));
  }

  /** The criterion and the evidence as text, in labelled sections. */
  public static String defaultUserMessage(Input input) {
    var text = new StringBuilder();
    text.append("Criterion:\n").append(input.criterion());
    text.append("\n\nThe user asked:\n").append(input.userMessage());
    text.append("\n\nThe agent replied:\n").append(input.reply());
    if (!input.toolCalls().isEmpty()) {
      text.append("\n\nTools called, in order:");
      for (var call : input.toolCalls()) {
        text.append("\n- ").append(call.name()).append(' ').append(call.arguments());
        call.result().ifPresent(result -> text.append(" -> ").append(result));
        call.error().ifPresent(error -> text.append(" -> failed: ").append(error));
      }
    }
    return text.toString();
  }
}
