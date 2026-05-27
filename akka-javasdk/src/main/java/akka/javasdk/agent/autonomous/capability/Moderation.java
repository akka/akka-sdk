/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous.capability;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.impl.agent.autonomous.capability.ModerationImpl;

/**
 * Declares that an agent can moderate turn-taking conversations between participant agents.
 * Supports patterns like debates, peer reviews, and negotiations.
 *
 * <p>Created via {@link Moderation#of}.
 */
public interface Moderation extends AgentCapability {

  /** Create a moderation capability with the given participant agent types. */
  @SafeVarargs
  static Moderation of(
      Class<? extends AutonomousAgent> first, Class<? extends AutonomousAgent>... rest) {
    return ModerationImpl.create(first, rest);
  }

  /** Maximum number of conversation rounds. Defaults to 5. */
  Moderation maxRounds(int max);

  /** Maximum LLM iterations a participant gets per turn before auto-submitting. Defaults to 10. */
  Moderation maxIterationsPerTurn(int max);

  /** Maximum number of concurrent conversations for this group. Defaults to 1. */
  Moderation maxConcurrentConversations(int max);
}
