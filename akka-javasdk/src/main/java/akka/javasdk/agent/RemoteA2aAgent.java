/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.annotation.DoNotInherit;
import akka.javasdk.impl.agent.RemoteA2aAgentImpl;
import java.time.Duration;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A remote agent that speaks the Agent2Agent (A2A) protocol. Each skill in the agent card of the
 * remote agent becomes a tool of the agent that uses it.
 *
 * <p>The runtime speaks A2A version 1.0 over JSON-RPC. The card of the remote agent must list a
 * JSON-RPC interface with protocol version 1.0.
 *
 * <p>Only the logical name is in code. The SDK reads the card URL, the authentication and the
 * allowed hosts from the configuration of the service under {@code
 * akka.javasdk.agent.a2a-client.agents.<name>} and checks them when the service starts, so they can
 * differ per environment.
 *
 * <p><strong>Security:</strong> Credentials are only sent over HTTPS. The runtime only sends
 * requests to the host and port of the agent card, or to hosts in the {@code allowed-hosts}
 * configuration of the agent. Content from the remote agent reaches the model only as a tool
 * result.
 *
 * <p>Not for user extension, create instances using {@link #named(String)}.
 */
@DoNotInherit
public interface RemoteA2aAgent {

  /**
   * @param name The logical name of the remote agent, as it is configured under {@code
   *     akka.javasdk.agent.a2a-client.agents}
   */
  static RemoteA2aAgent named(String name) {
    return new RemoteA2aAgentImpl(name);
  }

  /**
   * Define a filter to select which skills of the remote agent are passed on to the chat model. The
   * filter gets the skill id from the agent card. Skills that are filtered are not described to the
   * model and do not allow calls. Will override a previous call to {@link #withAllowedSkills(Set)}.
   *
   * <p>The A2A protocol has no field that selects a skill. The runtime sends the skill id as
   * metadata of the message, and the remote agent can ignore it. The filter chooses what the model
   * sees. It does not limit what the remote agent does with a message.
   *
   * <p>By default, all skills are allowed.
   */
  RemoteA2aAgent withSkillFilter(Predicate<String> skillFilter);

  /**
   * Define a set of allowed skill ids. Will override a previously defined {@link
   * #withSkillFilter(Predicate)}.
   *
   * <p>By default, all skills are allowed.
   */
  RemoteA2aAgent withAllowedSkills(Set<String> allowedSkills);

  /**
   * Define a set of allowed skill ids. Will override a previously defined {@link
   * #withSkillFilter(Predicate)}.
   *
   * <p>By default, all skills are allowed.
   */
  RemoteA2aAgent withAllowedSkills(String allowedSkill, String... moreAllowedSkills);

  /**
   * @param timeout A max time a call to the remote agent may take before it is failed. If not
   *     specified, the {@code request-timeout} of the agent in the configuration applies.
   */
  RemoteA2aAgent withTimeout(Duration timeout);
}
