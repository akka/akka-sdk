/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.client.ComponentClient;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * A {@link Judge} that asks a model through {@link JudgeAgent}. The verdict is one model call, not
 * deterministic, and consumes tokens.
 *
 * <p>The system message is {@link #DEFAULT_SYSTEM_MESSAGE}, or the one passed to {@link
 * #withSystemMessage}. The judge appends {@link #REPLY_FORMAT} to either, so the model always
 * replies with a JSON object with a {@code score} between 0 and 1 and a {@code reason}.
 *
 * <p>The user message is the criterion and the {@link Interaction} rendered as text by {@link
 * #defaultUserMessage}. {@link #withUserMessage} replaces that rendering, for example to write the
 * sections in another language.
 *
 * <p>The model is the one configured in {@code akka.javasdk.agent.model-provider}. {@link
 * #withModel} names another configuration path. A provider registered for {@link JudgeAgent} in the
 * TestKit settings takes precedence over both.
 *
 * <p>Each verdict is decided in a new session.
 */
public final class ModelBasedJudge implements Judge {

  /**
   * The system message a judge sends unless {@link #withSystemMessage} replaces it, before {@link
   * #REPLY_FORMAT}.
   */
  // tag::system-message[]
  public static final String DEFAULT_SYSTEM_MESSAGE =
      """
      You judge whether an agent's reply meets a stated criterion.

      You will be given the criterion, the message the user sent, the reply the agent produced,
      and the tools it called to produce that reply. Score how fully the reply meets the
      criterion, between 0 (does not meet it) and 1 (fully meets it). Judge the criterion you
      were given and nothing else: a reply you would have worded differently still meets a
      criterion it satisfies.
      """;

  // end::system-message[]

  /** Appended to every system message: the reply format the verdict is read from. */
  public static final String REPLY_FORMAT =
      """
      Reply as JSON: {"score": <number between 0 and 1>, "reason": "<one sentence>"}
      """;

  private final ComponentClient componentClient;
  private final String systemMessage;
  private final BiFunction<String, Interaction, String> userMessage;
  private final String modelConfigPath;

  ModelBasedJudge(
      ComponentClient componentClient,
      String systemMessage,
      BiFunction<String, Interaction, String> userMessage,
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
  static ModelBasedJudge backedBy(ComponentClient componentClient) {
    return new ModelBasedJudge(
        componentClient, DEFAULT_SYSTEM_MESSAGE, ModelBasedJudge::defaultUserMessage, "");
  }

  /** The same judge with another system message. {@link #REPLY_FORMAT} is appended to it. */
  public ModelBasedJudge withSystemMessage(String systemMessage) {
    return new ModelBasedJudge(componentClient, systemMessage, userMessage, modelConfigPath);
  }

  /**
   * The same judge with another rendering of the criterion and the interaction into the user
   * message. The rendering must carry what the system message asks the model to judge.
   */
  public ModelBasedJudge withUserMessage(BiFunction<String, Interaction, String> userMessage) {
    return new ModelBasedJudge(componentClient, systemMessage, userMessage, modelConfigPath);
  }

  /**
   * The same judge asking the model configured at this path, for example {@code
   * "eval.judge-model"}, in the same form as {@code akka.javasdk.agent.model-provider}.
   */
  public ModelBasedJudge withModel(String modelConfigPath) {
    if (modelConfigPath == null || modelConfigPath.isBlank())
      throw new IllegalArgumentException("modelConfigPath required");
    return new ModelBasedJudge(componentClient, systemMessage, userMessage, modelConfigPath);
  }

  /** The system message this judge sends, with {@link #REPLY_FORMAT} appended. */
  public String systemMessage() {
    return systemMessage.stripTrailing() + "\n\n" + REPLY_FORMAT;
  }

  /** The configuration path of the model this judge asks; empty for the default model. */
  public String modelConfigPath() {
    return modelConfigPath;
  }

  @Override
  public Verdict decide(String criterion, Interaction interaction) {
    return componentClient
        .forAgent()
        .inSession(UUID.randomUUID().toString())
        .method(JudgeAgent::decide)
        .invoke(
            new JudgeAgent.Request(
                systemMessage(), userMessage.apply(criterion, interaction), modelConfigPath));
  }

  /** The criterion and the interaction as text, in labeled sections. */
  public static String defaultUserMessage(String criterion, Interaction interaction) {
    var text = new StringBuilder();
    text.append("Criterion:\n").append(criterion);
    text.append("\n\nThe user asked:\n").append(interaction.userMessage());
    text.append("\n\nThe agent replied:\n").append(interaction.reply());
    if (!interaction.toolCalls().isEmpty()) {
      text.append("\n\nTools called, in order:");
      for (var call : interaction.toolCalls()) {
        text.append("\n- ").append(call.name()).append(' ').append(call.arguments());
        call.result().ifPresent(result -> text.append(" -> ").append(result));
        call.error().ifPresent(error -> text.append(" -> failed: ").append(error));
      }
    }
    return text.toString();
  }
}
