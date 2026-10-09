/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.Agent;
import akka.javasdk.impl.agent.autonomous.AgentDefinitionImpl;

/**
 * An autonomous AI agent component that operates independently to complete tasks.
 *
 * <p>Unlike a request-based {@link Agent}, an autonomous agent runs a durable execution loop: call
 * LLM, execute tools, check task status, repeat — until the task is complete or the iteration limit
 * is reached.
 *
 * <p>Subclasses must implement {@link #definition()} to configure the agent's tools, model
 * provider, capabilities, and optional instructions.
 *
 * <p><strong>Component Identification:</strong> The agent must be annotated with {@link
 * akka.javasdk.annotations.Component} providing a unique {@code id} and a non-empty {@code
 * description}. The description captures the agent's purpose and expected outcome: it is injected
 * into the model's system message and used by other agents when choosing a delegation or handoff
 * target.
 *
 * @see AgentDefinition
 */
public abstract class AutonomousAgent implements akka.javasdk.agent.AgentDelegationWorker {

  /**
   * Token usage statistics for an autonomous agent.
   *
   * <p>See {@link akka.javasdk.agent.Agent.TokenUsage} for the meaning of each count. A {@code
   * totalInputTokens} of 0 is replaced by {@code inputTokens}.
   *
   * @param inputTokens the input token count as the provider reports it
   * @param outputTokens tokens in the responses returned by the model
   * @param cacheReadInputTokens prompt tokens served from the provider's prompt cache
   * @param cacheWriteInputTokens prompt tokens written to the provider's prompt cache
   * @param totalInputTokens every prompt token consumed, counted once, whatever the provider
   */
  public record TokenUsage(
      int inputTokens,
      int outputTokens,
      int cacheReadInputTokens,
      int cacheWriteInputTokens,
      int totalInputTokens) {

    public TokenUsage {
      if (totalInputTokens == 0) {
        totalInputTokens = inputTokens;
      }
    }

    /** A usage with no prompt cache activity. */
    public TokenUsage(int inputTokens, int outputTokens) {
      this(inputTokens, outputTokens, 0, 0, inputTokens);
    }
  }

  /**
   * Define this autonomous agent. The definition configures the agent's tools, model provider,
   * guardrails, capabilities, and optional instructions.
   *
   * @return the agent definition
   */
  public abstract AgentDefinition definition();

  /** Start building an agent definition. */
  protected final AgentDefinition define() {
    return AgentDefinitionImpl.empty();
  }
}
