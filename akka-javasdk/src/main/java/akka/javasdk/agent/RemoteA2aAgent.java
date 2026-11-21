/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.annotation.DoNotInherit;
import akka.javasdk.impl.a2a.RemoteA2aAgentImpl;
import java.time.Duration;
import java.util.Set;

/**
 * A remote Agent2Agent (A2A) agent that an agent offers to its model as tools.
 *
 * <p>Pass it to {@link Agent.Effect.Builder#a2aAgents}. The agent reads the remote agent's Agent
 * Card and offers each skill on the card to the model as one tool. The tool takes a required {@code
 * message} text and an optional {@code data} object. When the model calls the tool, the agent sends
 * the message to the skill and returns the reply to the model. The A2A context id of the message is
 * derived from the session id and the agent name, so every call of a session to the agent has the
 * same context id and the session id does not leave the service.
 *
 * <p>The names of the tools are {@code <agent name>_<skill id>}. The agent name is the configured
 * name for {@link #named(String)}. For {@link #fromCardUrl(String)} and {@link
 * #fromService(String)} it is the name of a configuration under {@code
 * akka.javasdk.a2a.remote-agents} with the same {@code card-url}, or else the {@code name} on the
 * Agent Card. The same name is the {@code agentName} of {@link
 * ToolCallGuardrail.ToolOrigin.RemoteA2a}.
 *
 * <p>A failure of the remote agent, such as an error reply or a timeout, is returned to the model
 * as the tool's result, so that the model can decide what to do next. The agent request does not
 * fail.
 *
 * <p>Not for user extension, create instances using {@link #named(String)}, {@link
 * #fromCardUrl(String)} or {@link #fromService(String)}.
 */
@DoNotInherit
public interface RemoteA2aAgent {

  /**
   * A remote agent configured under {@code akka.javasdk.a2a.remote-agents.<name>}, with the
   * credentials and TLS settings of that configuration.
   *
   * @param name the name of the configuration entry
   */
  static RemoteA2aAgent named(String name) {
    return RemoteA2aAgentImpl.named(name);
  }

  /**
   * A remote agent given by the URL of its Agent Card. It has no credentials and no custom TLS
   * settings, and uses the settings under {@code akka.javasdk.a2a.defaults}.
   *
   * @param url the URL of the Agent Card, which must start with {@code http://} or {@code https://}
   */
  static RemoteA2aAgent fromCardUrl(String url) {
    return RemoteA2aAgentImpl.fromCardUrl(url);
  }

  /**
   * The A2A endpoint of another Akka service in the same project. The calls go to the service by
   * its name, as {@link RemoteMcpTools#fromService(String)} does, and carry the identity of this
   * service, so the other service's {@code @Acl} can allow it. It uses the settings under {@code
   * akka.javasdk.a2a.defaults}.
   *
   * @param serviceName the name of the other service
   */
  static RemoteA2aAgent fromService(String serviceName) {
    return RemoteA2aAgentImpl.fromService(serviceName);
  }

  /**
   * Offers only the skills with these ids to the model. Skills on the Agent Card that are not in
   * the set are not offered and cannot be called.
   *
   * <p>By default, every skill on the Agent Card is offered.
   */
  RemoteA2aAgent withAllowedSkills(Set<String> skillIds);

  /**
   * Offers only the skills with these ids to the model. Skills on the Agent Card that are not
   * listed are not offered and cannot be called.
   *
   * <p>By default, every skill on the Agent Card is offered.
   */
  RemoteA2aAgent withAllowedSkills(String skillId, String... moreSkillIds);

  /**
   * Limits how long a tool call waits for the remote agent's reply. When the time passes, the model
   * receives a timeout error as the tool's result.
   *
   * <p>By default, the {@code timeout} of the remote agent's configuration applies.
   */
  RemoteA2aAgent withTimeout(Duration timeout);

  /**
   * Hands a remote task that is still working after this time to a workflow of the SDK, so that the
   * tool call does not wait for it.
   *
   * <p>With this setting, the tool sends the message with {@code returnImmediately} and follows the
   * task for at most this time. When the task ends or needs input in that time, the tool returns
   * the reply as usual. Otherwise the tool returns the id of the task to the model, with a note
   * that the outcome follows later. A workflow of the SDK then follows the task, by push
   * notifications or by polling as {@link
   * akka.javasdk.a2a.RemoteA2aAgentClient.SendRequest#notifyWorkflow} describes, and adds the
   * outcome to the session memory of the calling agent as a user message when the task ends or
   * needs input. The model reads it with the next request in the session.
   *
   * <p>The SDK follows the task only in a service that configures a remote agent under {@code
   * akka.javasdk.a2a.remote-agents}, has a component that injects {@link
   * akka.javasdk.a2a.A2aClient}, or sets {@code akka.javasdk.a2a.remote-tasks.enabled = on}. In
   * another service the tool returns the state of the task without a hand-off, and the SDK logs a
   * warning once.
   *
   * <p>By default, the tool waits for the reply up to the timeout and returns the state of a task
   * that has not ended.
   */
  RemoteA2aAgent handOffTasksStillWorkingAfter(Duration time);
}
