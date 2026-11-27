/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.annotation.InternalApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * A mock of a remote A2A agent for tests. Register it with {@link
 * TestKit.Settings#withMockedA2aAgent(MockA2aAgent)}.
 *
 * <p>The testkit serves an agent card with the skills of the mock and answers calls to them. It
 * also sets the configuration of the agent name, so {@code RemoteA2aAgent.named(name)} in the
 * service under test reaches the mock, without any other configuration. For the name of the mock,
 * the testkit replaces the authentication and the allowed hosts that the configuration of the
 * service has, as the mock is plain HTTP.
 *
 * <p>The mock speaks A2A version 1.0 over JSON-RPC, as the runtime does. Like a 1.0 server, it
 * rejects a request that does not have the {@code A2A-Version} header.
 *
 * <p>The mock picks the skill from the {@code skillId} in the metadata of the message, which the
 * runtime sends. The A2A protocol has no field to select a skill, so a real remote agent can ignore
 * it, and the mock does not show that.
 *
 * <p>A skill handler receives the text of the message from the agent under test and returns the
 * text of the reply. When the handler throws, the remote agent reports a failed task, which the
 * agent under test sees as an error result. Handlers can run concurrently, so state that they share
 * with the test must be thread-safe.
 */
public final class MockA2aAgent {

  /** INTERNAL API: A skill of the mock. */
  @InternalApi
  public record Skill(String id, String description, Function<String, String> handler) {}

  private final String name;
  private final List<Skill> skills;

  private MockA2aAgent(String name, List<Skill> skills) {
    this.name = name;
    this.skills = skills;
  }

  /**
   * @param name The logical name of the remote agent, as in {@code RemoteA2aAgent.named(name)}.
   */
  public static MockA2aAgent named(String name) {
    if (name == null || !name.matches("[A-Za-z0-9._-]+"))
      throw new IllegalArgumentException(
          "name must only have letters, digits, '.', '_' and '-', but was [" + name + "]");
    return new MockA2aAgent(name, List.of());
  }

  /**
   * Add a skill to the mock. The model sees it as the tool {@code <name>__<id>}.
   *
   * @param id The skill id.
   * @param description The description of the skill, shown to the model.
   * @param handler Maps the text of the message to the text of the reply.
   */
  public MockA2aAgent withSkill(String id, String description, Function<String, String> handler) {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be empty");
    var updated = new ArrayList<>(skills);
    updated.add(new Skill(id, description, handler));
    return new MockA2aAgent(name, Collections.unmodifiableList(updated));
  }

  /** INTERNAL API */
  @InternalApi
  public String name() {
    return name;
  }

  /** INTERNAL API */
  @InternalApi
  public List<Skill> skills() {
    return skills;
  }
}
