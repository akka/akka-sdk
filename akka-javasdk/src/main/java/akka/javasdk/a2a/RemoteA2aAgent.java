/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.annotation.DoNotInherit;
import akka.javasdk.impl.a2a.RemoteA2aAgentImpl;
import java.time.Duration;
import java.util.Set;

/**
 * A remote Agent2Agent (A2A) agent that an agent offers to its model as tools.
 *
 * <p>Pass it to {@link akka.javasdk.agent.Agent.Effect.Builder#a2aAgents}. The agent reads the
 * remote agent's Agent Card and offers each skill on the card to the model as one tool. The tool
 * takes a required {@code message} text and an optional {@code data} object. When the model calls
 * the tool, the agent sends the message to the skill with the session id as the A2A context id, and
 * returns the reply to the model.
 *
 * <p>A failure of the remote agent, such as an error reply or a timeout, is returned to the model
 * as the tool's result, so that the model can decide what to do next. The agent request does not
 * fail.
 *
 * <p>Not for user extension, create instances using {@link #named(String)} or {@link
 * #fromCard(String)}.
 */
@DoNotInherit
public interface RemoteA2aAgent {

  /**
   * A remote agent configured under {@code akka.javasdk.a2a.remote-agents.<name>}, with the
   * credentials and TLS settings of that configuration. The names of its tools start with the name.
   *
   * @param name the name of the configuration entry
   */
  static RemoteA2aAgent named(String name) {
    return RemoteA2aAgentImpl.named(name);
  }

  /**
   * A remote agent given by the URL of its Agent Card. It has no credentials and no custom TLS
   * settings, and uses the settings under {@code akka.javasdk.a2a.defaults}. The names of its tools
   * start with the {@code name} on the Agent Card.
   *
   * @param url the URL of the Agent Card, which must start with {@code http://} or {@code https://}
   */
  static RemoteA2aAgent fromCard(String url) {
    return RemoteA2aAgentImpl.fromCard(url);
  }

  /**
   * Offers only the skills with these ids to the model. Skills on the Agent Card that are not in
   * the set are not offered and cannot be called.
   *
   * <p>By default, every skill on the Agent Card is offered.
   */
  RemoteA2aAgent withAllowedSkills(Set<String> skillIds);

  /**
   * Limits how long a tool call waits for the remote agent's reply. When the time passes, the model
   * receives a timeout error as the tool's result.
   *
   * <p>By default, the {@code timeout} of the remote agent's configuration applies.
   */
  RemoteA2aAgent withTimeout(Duration timeout);

  /**
   * Sets the hand-off time for remote tasks that are still working.
   *
   * <p>A remote agent can reply with a task that is not finished, such as a task that is submitted
   * or working. The tool call returns the id and the state of that task to the model as text, and
   * does not wait for the task to finish.
   */
  RemoteA2aAgent handOffTasksStillWorkingAfter(Duration after);
}
